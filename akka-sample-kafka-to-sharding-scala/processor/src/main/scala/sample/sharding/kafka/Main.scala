package sample.sharding.kafka

import akka.actor.typed.ActorSystem
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.management.scaladsl.AkkaManagement
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

object Main {
  def main(args: Array[String]): Unit = {

    def isInt(s: String): Boolean = s.matches("""\d+""")

    args.toList match {
      case portString :: managementPort :: frontEndPort :: Nil
          if isInt(portString) && isInt(managementPort) && isInt(frontEndPort) =>
        startNode(portString.toInt, managementPort.toInt, frontEndPort.toInt)
      case _ =>
        throw new IllegalArgumentException("usage: <remotingPort> <managementPort> <frontEndPort>")
    }
  }

  def startNode(remotingPort: Int, akkaManagementPort: Int, frontEndPort: Int): Unit = {
    ActorSystem(Behaviors.setup[MemberUp] {
      ctx =>
        implicit val mat = Materializer.createMaterializer(ctx.system.toClassic)
        implicit val ec = ctx.executionContext
        AkkaManagement(ctx.system.toClassic).start()
        // maybe don't start until part of the cluster, or add health check
        val binding = startGrpc(ctx.system, mat, frontEndPort)
        val cluster = Cluster(ctx.system)
        cluster.subscriptions.tell(Subscribe(ctx.self, classOf[MemberUp]))
        Behaviors
          .receiveMessage[MemberUp] {
            case MemberUp(member) if member.uniqueAddress == cluster.selfMember.uniqueAddress =>
              ctx.log.info("Joined the cluster. Starting sharding and kafka processor")
              val eventProcessor = ctx.spawn[Nothing](UserEventsKafkaProcessor(), "kafka-event-processor")
              ctx.watch(eventProcessor)
              Behaviors.same
            case MemberUp(member) =>
              ctx.log.info("Member up {}", member)
              Behaviors.same
          }
          .receiveSignal {
            case (ctx, Terminated(_)) =>
              ctx.log.warn("Kafka event processor stopped. Shutting down")
              binding.map(_.unbind())
              Behaviors.stopped
          }
    }, "KafkaToSharding", config(remotingPort, akkaManagementPort))

    def startGrpc(system: ActorSystem[_], mat: Materializer, frontEndPort: Int): Future[Http.ServerBinding] = {
      val service: HttpRequest => Future[HttpResponse] =
        UserServiceHandler(new UserGrpcService(system))(mat, system.toClassic)
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
