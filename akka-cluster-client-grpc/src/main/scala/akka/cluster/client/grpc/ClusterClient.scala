/**
 * Copyright (C) 2009-2019 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.client.grpc

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Deploy
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.Props
import akka.actor.Terminated
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import akka.stream.KillSwitches
import akka.stream.OverflowStrategy
import akka.stream.SharedKillSwitch
import akka.stream.WatchedActorTerminatedException
import akka.stream.scaladsl.Source
import com.typesafe.config.Config

object ClusterClient {

  /**
   * Scala API: Factory method for `ClusterClient` [[akka.actor.Props]].
   */
  def props(settings: ClusterClientSettings)(implicit materializer: ActorMaterializer): Props =
    Props(new ClusterClient(settings)).withDeploy(Deploy.local)

  sealed trait Command

  @SerialVersionUID(1L)
  final case class Send(path: String, msg: Any, localAffinity: Boolean) extends Command {

    /**
     * Convenience constructor with `localAffinity` false
     */
    def this(path: String, msg: Any) = this(path, msg, localAffinity = false)
  }

  /**
   * More efficient than `Send` for single request-reply interaction
   */
  @SerialVersionUID(1L)
  final case class SendAsk(path: String, msg: Any, localAffinity: Boolean) extends Command {

    /**
     * Convenience constructor with `localAffinity` false
     */
    def this(path: String, msg: Any) = this(path, msg, localAffinity = false)
  }

  @SerialVersionUID(1L)
  final case class SendToAll(path: String, msg: Any) extends Command
  @SerialVersionUID(1L)
  final case class Publish(topic: String, msg: Any) extends Command

  private def createClientStub()(implicit mat: ActorMaterializer): ClusterClientReceptionistServiceClient = {
    val grpcClientSettings = GrpcClientSettings
    // FIXME service discovery
      .connectToServiceAt("127.0.0.1", 50051)(mat.system)
      .withDeadline(3.second) // FIXME config
      .withTls(false)
    implicit val ec: ExecutionContext = mat.executionContext
    ClusterClientReceptionistServiceClient(grpcClientSettings)
  }

  private def newSession(
      receptionistServiceClient: ClusterClientReceptionistServiceClient,
      sender: ActorRef,
      killSwitch: SharedKillSwitch,
      log: LoggingAdapter,
      serialization: ClusterClientSerialization)(implicit mat: ActorMaterializer): Future[ActorRef] = {
    val sessionReqRefPromise = Promise[ActorRef]()
    log.info("New session for {}", sender)
    receptionistServiceClient
      .newSession(
        Source
          .actorRef[Any](
            bufferSize = 1000, // FIXME config
            overflowStrategy = OverflowStrategy.dropNew)
          .via(killSwitch.flow)
          .map {
            case send: Send =>
              val payload = serialization.serializePayload(send.msg)
              Req().withSend(SendReq(send.path, send.localAffinity, Some(payload)))
            case sendToAll: SendToAll =>
              val payload = serialization.serializePayload(sendToAll.msg)
              Req().withSendToAll(SendToAllReq(sendToAll.path, Some(payload)))
            case publish: Publish =>
              val payload = serialization.serializePayload(publish.msg)
              Req().withPublish(PublishReq(publish.topic, Some(payload)))
          }
          .mapMaterializedValue(sessionReqRef => {
            sessionReqRefPromise.success(sessionReqRef)
            NotUsed
          }))
      .watch(sender) // end session when original sender terminates
      .recoverWithRetries(-1, { case _: WatchedActorTerminatedException => Source.empty })
      .map { rsp =>
        serialization.deserializePayload(rsp.payload.get)
      }
      .runForeach(sender ! _)
      .onComplete { result =>
        log.info("Session completed for {} with {}", sender, result)
      }(mat.executionContext)

    sessionReqRefPromise.future
  }

  private def askSend(
      receptionistServiceClient: ClusterClientReceptionistServiceClient,
      send: SendAsk,
      serialization: ClusterClientSerialization)(implicit ec: ExecutionContext): Future[Any] = {
    val payload = serialization.serializePayload(send.msg)
    val sendReq = SendReq(send.path, send.localAffinity, Some(payload))
    receptionistServiceClient.askSend(sendReq).map { rsp =>
      serialization.deserializePayload(rsp.payload.get)
    }
  }
}

/**
 * This actor is intended to be used on an external node that is not member
 * of the cluster. It acts like a gateway for sending messages to actors
 * somewhere in the cluster. With service discovery and Akka gRPC it will establish
 * a connection to a [[ClusterClientReceptionist]] somewhere in the cluster.
 *
 * It will monitor the connection to the receptionist and establish a new connection if
 * the link goes down.
 *
 * You can send messages via the `ClusterClient` to any actor in the cluster
 * that is registered in the [[ClusterClientReceptionist]].
 * Messages are wrapped in [[ClusterClient#Send]], [[ClusterClient#SendToAll]]
 * or [[ClusterClient#Publish]].
 *
 * 1. [[ClusterClient#Send]] -
 * The message will be delivered to one recipient with a matching path, if any such
 * exists. If several entries match the path the message will be delivered
 * to one random destination. The sender of the message can specify that local
 * affinity is preferred, i.e. the message is sent to an actor in the same local actor
 * system as the used receptionist actor, if any such exists, otherwise random to any other
 * matching entry.
 *
 * 2. [[ClusterClient#SendToAll]] -
 * The message will be delivered to all recipients with a matching path.
 *
 * 3. [[ClusterClient#Publish]] -
 * The message will be delivered to all recipients Actors that have been registered as subscribers to
 * to the named topic.
 *
 *  Use the factory method [[ClusterClient#props]]) to create the
 * [[akka.actor.Props]] for the actor.
 *
 * If the receptionist is not currently available, the client will buffer the messages
 * and then deliver them when the connection to the receptionist has been established.
 * The size of the buffer is configurable and it can be disabled by using a buffer size
 * of 0. When the buffer is full old messages will be dropped when new messages are sent
 * via the client.
 *
 * Note that this is a best effort implementation: messages can always be lost due to the distributed
 * nature of the actors involved.
 */
final class ClusterClient(settings: ClusterClientSettings)(implicit materializer: ActorMaterializer)
    extends Actor
    with ActorLogging {

  import ClusterClient._

  val serialization = new ClusterClientSerialization(context.system)

  private val receptionistServiceClient: ClusterClientReceptionistServiceClient = createClientStub()

  // Original sender -> stream Source.actorRef of the session
  private var sessionRef: Map[ActorRef, Future[ActorRef]] = Map.empty

  private val killSwitch = KillSwitches.shared(self.path.name)

  override def postStop(): Unit = {
    killSwitch.shutdown()
    super.postStop()
  }

  def receive: Receive = {
    case send: SendAsk =>
      import akka.pattern.pipe
      import context.dispatcher
      askSend(receptionistServiceClient, send, serialization).pipeTo(sender())

    case cmd: Command =>
      // Send or Publish
      val originalSender = sender()
      val session = sessionRef.get(originalSender) match {
        case Some(ses) => ses
        case None =>
          val ses = newSession(receptionistServiceClient, originalSender, killSwitch, log, serialization)
          sessionRef = sessionRef.updated(originalSender, ses)
          ses
      }

      context.watch(originalSender)

      import context.dispatcher
      session.foreach(_ ! cmd)

    case Terminated(ref) =>
      sessionRef -= ref
  }

}

object ClusterClientSettings {

  /**
   * Create settings from the default configuration
   * `akka.cluster.client.grpc`.
   */
  def apply(system: ActorSystem): ClusterClientSettings =
    apply(system.settings.config.getConfig("akka.cluster.client.grpc"))

  /**
   * Create settings from a configuration with the same layout as
   * the default configuration `akka.cluster.client.grpc`.
   */
  def apply(config: Config): ClusterClientSettings = {
    new ClusterClientSettings
  }

  /**
   * Java API: Create settings from the default configuration
   * `akka.cluster.client.grpc`.
   */
  def create(system: ActorSystem): ClusterClientSettings = apply(system)

  /**
   * Java API: Create settings from a configuration with the same layout as
   * the default configuration `akka.cluster.client.grpc`.
   */
  def create(config: Config): ClusterClientSettings = apply(config)

}

// FIXME
final class ClusterClientSettings() extends NoSerializationVerificationNeeded
