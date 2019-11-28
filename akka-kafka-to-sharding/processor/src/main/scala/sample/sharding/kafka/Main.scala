package sample.sharding.kafka

import akka.actor.typed.ActorSystem
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

object Main {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case portString :: managementPort :: Nil if portString.matches("""\d+""") && managementPort.matches("""\d+""") =>
        startNode(portString.toInt, managementPort.toInt)
      case _ =>
        throw new IllegalArgumentException("usage: <remotingPort> <managementPort>")
    }
  }

  def startNode(remotingPort: Int, akkaManagementPort: Int): Unit = {
    ActorSystem(Behaviors.setup[MemberUp] {
      ctx =>
        AkkaManagement(ctx.system.toClassic).start()
        val cluster = Cluster(ctx.system)
        cluster.subscriptions.tell(Subscribe(ctx.self, classOf[MemberUp]))
        val onMemberUp = Behaviors.receiveMessage[MemberUp] {
          case MemberUp(member) if member.uniqueAddress == cluster.selfMember.uniqueAddress =>
            ctx.log.info("Joined the cluster. Starting sharding and kafka processor")
            UserEvents.init(ctx.system)
            val eventProcessor = ctx.spawn[Nothing](UserEventsKafkaProcessor(), "kafka-event-procoessor")
            ctx.watch(eventProcessor)
            Behaviors.same
          case MemberUp(member) =>
            ctx.log.info("Member up {}", member)
            Behaviors.same
        }

        onMemberUp.receiveSignal {
          case (ctx, Terminated(_)) =>
            ctx.log.warn("Kafka event processor stopped. Shutting down")
            Behaviors.stopped
        }
    }, "KafkaToSharding", config(remotingPort, akkaManagementPort))

    def config(port: Int, managementPort: Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = $managementPort
       """).withFallback(ConfigFactory.load())

  }

}
