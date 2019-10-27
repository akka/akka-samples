package sample.cluster.transformation

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory

object App {

  def main(args: Array[String]): Unit = {
    // starting 2 frontend nodes and 3 backend nodes
    if (args.isEmpty) {
      startup("backend", 25251)
      startup("backend", 25252)
      startup("frontend", 0)
      startup("frontend", 0)
      startup("frontend", 0)
    } else {
      require(args.size == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
    }
  }

  def startup(role: String, port: Int): Unit = {
    // Override the configuration of the port and role
    val config = ConfigFactory.parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [frontend]
        """)
      .withFallback(ConfigFactory.load())

    val rootBehavior = Behaviors.setup[Nothing] { ctx =>

      // start a different set of children depending on role
      role match {
        case "backend" =>
          (1 to 4).foreach { n =>
            ctx.spawn(Worker(), s"Worker$n")
          }
        case "frontend" =>
          ctx.spawn(Frontend(), "Frontend")

        case unknown => throw new IllegalArgumentException(s"Unknown role $role")
      }

      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "TransformationSystem", config)

  }

}