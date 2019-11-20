package sample.sharding.kafka

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
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
    ActorSystem(Behaviors.setup[Void] { ctx =>
      AkkaManagement(ctx.system.toClassic).start()
      UserEvents.init(ctx.system)
      ctx.spawn(UserEventsKafkaProcessor(), "kafka-event-procoessor")
      Behaviors.empty[Void]
    }, "KafkaToSharding", config(remotingPort, akkaManagementPort))

    def config(port: Int, managementPort: Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.management.http.port = $managementPort
       """).withFallback(ConfigFactory.load())

  }

}
