package sample.sharding

import scala.util.Random

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }

/**
 * See the README.md for starting each node with sbt.
 */
object KillrWeather {
  import akka.{ actor => classic }
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.server.Route

  private val random = new Random()

  private def nextHttpPort: Int = KillrWeather.random.nextInt(8080)

  def main(args: Array[String]): Unit = {

    val ports = args.headOption match {
      case Some(port) => Seq(port)
      case _          =>
        // In a production application you wouldn't typically start multiple ActorSystem instances in the
        // same JVM, here we do it to easily demonstrate these ActorSystems (which would be in separate JVM's)
        // talking to each other.
        Seq(2551, 2552, 0)
    }

    ports.foreach { port =>
      val config =
        ConfigFactory.parseString(s"""
          akka.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load("application.conf"))

      // Create an Akka system and an actor that starts the sharding
      // and receives messages from device.
      val httpPort = if (port == 2551) 8081 else nextHttpPort
      startNode(config, httpPort)
    }
  }

  def startNode(config: Config, httpPort: Int): Unit = {

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val guardian = context.spawn(Guardian(), "guardian")
      context.watch(guardian)

      val routes = new WeatherRoutes(guardian)(context.system)
      startHttpServer(routes.weather, httpPort, context.system)

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "KillrWeather", config)
  }

  private def startHttpServer(routes: Route, port: Int, system: ActorSystem[_]): Unit = {
    import system.executionContext
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: classic.ActorSystem = system.toClassic

    Http().bindAndHandle(routes, "localhost", port).onComplete {
      case scala.util.Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case scala.util.Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
