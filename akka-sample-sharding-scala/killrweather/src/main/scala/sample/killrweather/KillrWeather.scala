package sample.killrweather

import scala.util.Random

import akka.actor.AddressFromURIString
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

/**
 * See the README.md for starting each node with sbt.
 */
object KillrWeather {

  private val random = new Random()

  def main(args: Array[String]): Unit = {

    val seedNodes = akka.japi.Util
      .immutableSeq(ConfigFactory.load().getStringList("akka.cluster.seed-nodes"))
      .flatMap { case AddressFromURIString(s) => s.port }

    val ports = args.headOption match {
      case Some(port) => Seq(port.toInt)
      case _          =>
        // In a production application you wouldn't typically start multiple ActorSystem instances in the
        // same JVM, here we do it to easily demonstrate these ActorSystems (which would be in separate JVM's)
        // talking to each other.
        seedNodes ++ Seq(0)
    }

    val from = (akkaPort: Int) => if (!seedNodes.contains(akkaPort)) 8081 else s"80${random.nextInt(80)}".toInt

    for {
      akkaPort    <- ports
      weatherPort <- findHttpPort(from(akkaPort))
    } startNode(config(akkaPort), weatherPort)
  }

  /** Create an Akka system and an actor that starts the sharding
   * and receives messages from edge devices.
   */
  private def startNode(config: Config, httpPort: Int): Unit = {

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val guardian = context.spawn(Guardian(), "guardian")
      context.watch(guardian)

      val routes = new WeatherRoutes(guardian)(context.system)
      new WeatherServer(routes.weather, httpPort, context.system).start()

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "KillrWeather", config)
  }

  private def config(port: Int): Config =
    ConfigFactory.parseString(s"""
       akka.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load())

}
