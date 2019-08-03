/**
 * Copyright (C) 2009-2019 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.cluster.client.grpc

import scala.concurrent.Future

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.NoSerializationVerificationNeeded
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpConnectionContext
import akka.http.scaladsl.UseHttp2.Always
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

object ClusterClientReceptionist extends ExtensionId[ClusterClientReceptionist] with ExtensionIdProvider {
  override def get(system: ActorSystem): ClusterClientReceptionist =
    super.get(system)

  override def lookup() = ClusterClientReceptionist

  override def createExtension(system: ExtendedActorSystem): ClusterClientReceptionist =
    new ClusterClientReceptionist(system)
}

/**
 * Extension that starts gRPC service and accompanying `akka.cluster.pubsub.DistributedPubSubMediator`
 * with settings defined in config section `akka.cluster.client.grpc.receptionist`.
 * The `akka.cluster.pubsub.DistributedPubSubMediator` is started by the `akka.cluster.pubsub.DistributedPubSub`
 * extension.
 */
final class ClusterClientReceptionist(system: ExtendedActorSystem) extends Extension {

  private val config =
    system.settings.config.getConfig("akka.cluster.client.grpc.receptionist")
  private val role: Option[String] = config.getString("role") match {
    case "" => None
    case r  => Some(r)
  }

  private val log = Logging(system, getClass)

  /**
   * Returns true if this member is not tagged with the role configured for the
   * receptionist.
   */
  def isTerminated: Boolean =
    Cluster(system).isTerminated || !role.forall(Cluster(system).selfRoles.contains)

  /**
   * Register the actors that should be reachable for the clients in this [[DistributedPubSubMediator]].
   */
  private def pubSubMediator: ActorRef = DistributedPubSub(system).mediator

  /**
   * Register an actor that should be reachable for the clients.
   * The clients can send messages to this actor with `Send` or `SendToAll` using
   * the path elements of the `ActorRef`, e.g. `"/user/myservice"`.
   */
  def registerService(actor: ActorRef): Unit =
    pubSubMediator ! DistributedPubSubMediator.Put(actor)

  /**
   * A registered actor will be automatically unregistered when terminated,
   * but it can also be explicitly unregistered before termination.
   */
  def unregisterService(actor: ActorRef): Unit =
    pubSubMediator ! DistributedPubSubMediator.Remove(actor.path.toStringWithoutAddress)

  /**
   * Register an actor that should be reachable for the clients to a named topic.
   * Several actors can be registered to the same topic name, and all will receive
   * published messages.
   * The client can publish messages to this topic with `Publish`.
   */
  def registerSubscriber(topic: String, actor: ActorRef): Unit =
    pubSubMediator ! DistributedPubSubMediator.Subscribe(topic, actor)

  /**
   * A registered subscriber will be automatically unregistered when terminated,
   * but it can also be explicitly unregistered before termination.
   */
  def unregisterSubscriber(topic: String, actor: ActorRef): Unit =
    pubSubMediator ! DistributedPubSubMediator.Unsubscribe(topic, actor)

  val settings: ClusterReceptionistSettings = ClusterReceptionistSettings(config)

  private val server: Future[Http.ServerBinding] = {
    log.info("Starting ClusterClientReceptionist gRPC server at {}", settings.hostPort)

    implicit val sys = system
    implicit val materializer = ActorMaterializer()

    val serialization = new ClusterClientSerialization(system)

    val service: HttpRequest => Future[HttpResponse] =
      ClusterClientReceptionistServiceHandler(
        new ClusterClientReceptionistGrpcImpl(pubSubMediator, serialization)(materializer, log))

    Http().bindAndHandleAsync(
      service,
      interface = settings.hostPort.hostname,
      settings.hostPort.port,
      connectionContext = HttpConnectionContext(http2 = Always))
  }

  server.onComplete { result =>
    log.info("ClusterClientReceptionist gRPC server stopped: {}", result)
  }(system.dispatcher)

}

object ClusterReceptionistSettings {

  /**
   * Create settings from the default configuration
   * `akka.cluster.client.grpc.receptionist`.
   */
  def apply(system: ActorSystem): ClusterReceptionistSettings =
    apply(system.settings.config.getConfig("akka.cluster.client.grpc.receptionist"))

  /**
   * Create settings from a configuration with the same layout as
   * the default configuration `akka.cluster.client.grpc.receptionist`.
   */
  def apply(config: Config): ClusterReceptionistSettings =
    new ClusterReceptionistSettings(
      hostPort = HostPort(config.getString("canonical.hostname"), config.getInt("canonical.port")),
      role = roleOption(config.getString("role")))

  /**
   * Java API: Create settings from the default configuration
   * `akka.cluster.client.grpc.receptionist`.
   */
  def create(system: ActorSystem): ClusterReceptionistSettings = apply(system)

  /**
   * Java API: Create settings from a configuration with the same layout as
   * the default configuration `akka.cluster.client.grpc.receptionist`.
   */
  def create(config: Config): ClusterReceptionistSettings = apply(config)

  /**
   * INTERNAL API
   */
  private[akka] def roleOption(role: String): Option[String] =
    if (role == "") None else Option(role)

}

/**
 * @param role Start the receptionist on members tagged with this role.
 *   All members are used if undefined.
 */
final class ClusterReceptionistSettings(val hostPort: HostPort, val role: Option[String])
    extends NoSerializationVerificationNeeded {

  def withRole(role: String): ClusterReceptionistSettings =
    copy(role = ClusterReceptionistSettings.roleOption(role))

  def withRole(role: Option[String]): ClusterReceptionistSettings =
    copy(role = role)

  private def copy(hostPort: HostPort = hostPort, role: Option[String] = role): ClusterReceptionistSettings =
    new ClusterReceptionistSettings(hostPort, role)
}

object HostPort {
  def fromString(s: String): HostPort = {
    val parts = s.split(":")
    HostPort(parts(0), parts(1).toInt)
  }
}

final case class HostPort(hostname: String, port: Int) {
  override def toString: String = s"$hostname:$port"
}
