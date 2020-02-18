package sample.sharding.kafka

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.sharding.typed.ShardingMessageExtractor
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait Command
case object NodeMemberUp extends Command
case object StartProcessor extends Command
final case class MessageExtractor(extractor: ShardingMessageExtractor[UserEvents.Message, UserEvents.Message]) extends Command

object Main {
  def main(args: Array[String]): Unit = {

    def isInt(s: String): Boolean = s.matches("""\d+""")

    args.toList match {
      case portString :: managementPort :: frontEndPort :: Nil
          if isInt(portString) && isInt(managementPort) && isInt(frontEndPort) =>
        init(portString.toInt, managementPort.toInt, frontEndPort.toInt)
      case _ =>
        throw new IllegalArgumentException("usage: <remotingPort> <managementPort> <frontEndPort>")
    }
  }

  def init(remotingPort: Int, akkaManagementPort: Int, frontEndPort: Int): Unit = {
    ActorSystem(Behaviors.setup[Command] {
      ctx =>
        AkkaManagement(ctx.system.toClassic).start()

        val cluster = Cluster(ctx.system)
        val subscriber = ctx.spawn(clusterUpSubscriber(cluster, ctx.self), "cluster-subscriber")
        cluster.subscriptions.tell(Subscribe(subscriber, classOf[MemberUp]))

        ctx.pipeToSelf(UserEvents.messageExtractor(ctx.system)) {
          case Success(extractor) => MessageExtractor(extractor)
          case Failure(ex) => throw new Exception(ex)
        }

        starting()
    }, "KafkaToSharding", config(remotingPort, akkaManagementPort))

    def starting(extractor: Option[ShardingMessageExtractor[UserEvents.Message, UserEvents.Message]] = None): Behavior[Command] = Behaviors
      .receive[Command] {
        case (ctx, MessageExtractor(extractor)) =>
          ctx.self.tell(StartProcessor)
          starting(Some(extractor))
        case (ctx, StartProcessor) if extractor.isDefined =>
          val messageExtractor = extractor.get
          val processorSettings = ProcessorConfig(ctx.system.settings.config.getConfig("kafka-to-sharding-processor"))
          UserEvents.init(ctx.system, messageExtractor, processorSettings.groupId)
          val eventProcessor = ctx.spawn[Nothing](UserEventsKafkaProcessor(messageExtractor), "kafka-event-processor")
          ctx.watch(eventProcessor)
          ctx.log.info("Processor started.")
          val binding: Future[Http.ServerBinding] = startGrpc(ctx.system, frontEndPort, messageExtractor)
          running(binding, eventProcessor)
        case (ctx, StartProcessor) =>
          Behaviors.same
      }

    def running(binding: Future[Http.ServerBinding], processor: ActorRef[Nothing]): Behavior[Command] = Behaviors
      .receiveSignal {
        case (ctx, Terminated(`processor`)) =>
          ctx.log.warn("Kafka event processor stopped. Shutting down")
          binding.map(_.unbind())(ctx.executionContext)
          Behaviors.stopped
      }

    def clusterUpSubscriber(cluster: Cluster, parent: ActorRef[Command]): Behavior[MemberUp] = Behaviors.setup[MemberUp] {
      ctx =>
        Behaviors
          .receiveMessage[MemberUp] {
            case MemberUp(member) if member.uniqueAddress == cluster.selfMember.uniqueAddress =>
              ctx.log.info("Joined the cluster. Starting sharding and kafka processor")
              parent.tell(StartProcessor)
              Behaviors.same
            case MemberUp(member) =>
              ctx.log.info("Member up {}", member)
              Behaviors.same
          }
    }

    def startGrpc(system: ActorSystem[_], frontEndPort: Int, extractor: ShardingMessageExtractor[UserEvents.Message, UserEvents.Message]): Future[Http.ServerBinding] = {
      val mat = Materializer.createMaterializer(system.toClassic)
      val service: HttpRequest => Future[HttpResponse] =
        UserServiceHandler(new UserGrpcService(system, extractor))(mat, system.toClassic)
      Http()(system.toClassic).bindAndHandleAsync(
        service,
        interface = "127.0.0.1",
        port = frontEndPort,
        connectionContext = HttpConnectionContext())(mat)

    }

    def config(port: Int, managementPort: Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = $managementPort
       """).withFallback(ConfigFactory.load())

  }
}
