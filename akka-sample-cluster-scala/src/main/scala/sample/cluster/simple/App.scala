package sample.cluster.simple

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object App {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }

  def startup(ports: Seq[String]): Unit = {
    ports.foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString(s"""
        akka.remote.artery.canonical.port=$port
        """).withFallback(ConfigFactory.load())

      // Create an Akka system
      val rootBehavior = Behaviors.setup[Nothing] { context =>
        // Create an actor that handles cluster domain events
        context.spawn(ClusterListener(), "ClusterListener")

        Behaviors.empty
      }
      val system = ActorSystem[Nothing](rootBehavior, "ClusterSystem", config)

    }
  }
}
