package sample.sharding

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }
import com.typesafe.config.{ Config, ConfigFactory }

import scala.util.Random

/**
 * See the README.md for starting each node with sbt.
 */
object KillrWeather {
  import akka.{ actor => classic }
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.server.Route

  val random = new Random()

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
      startNode(config)
    }
  }

  def startNode(config: Config): Unit = {

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val guardian = context.spawn(Guardian(), "guardian")
      context.watch(guardian)

      val routes = new WeatherRoutes(guardian)(context.system)
      startHttpServer(routes.weather, context.system)

      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "KillrWeather", config)
  }

  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    import system.executionContext
    import akka.actor.typed.scaladsl.adapter._
    implicit val classicSystem: classic.ActorSystem = system.toClassic

    val httpPort = KillrWeather.random.nextInt(8080)

    val futureBinding = Http().bindAndHandle(routes, "localhost", httpPort)
    futureBinding.onComplete {
      case scala.util.Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case scala.util.Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}

object Guardian {

  import Protocol._

  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(stations: Set[WeatherStationId]): Behavior[Command] =
    Behaviors.setup { context =>
      TemperatureDevice.init(context.system)
      val sharding = ClusterSharding(context.system)

      def sharded(typeKey: EntityTypeKey[Command], id: Int) =
        sharding.entityRefFor(typeKey, id.toString)

      Behaviors.receiveMessage {
        case cmd @ UpdateDevice(stationId, deviceId, _) =>
          if (stations.contains(stationId)) {
            sharded(TemperatureDevice.TypeKey, deviceId) ! cmd
          }
          Behaviors.same

        case GetWeatherStations(replyTo) =>
          replyTo ! WeatherStations(stations)
          Behaviors.same

        case AddWeatherStation(sid) =>
          registry(stations = stations + sid)

        case data: Data =>
          context.log.debug(s"Received $data from stationId: ${data.stationId}")
          sharded(TemperatureDevice.TypeKey, data.deviceId) ! data

          Behaviors.same

        case Aggregate(sid, did, average, latest, readings) =>
          if (readings > 0)
            context.log.info(
              s"Temperature[stationId=$sid, deviceId=$did, latest=$latest, average=$average, readings=$readings]")

          Behaviors.same

        case cmd @ GetAggregate(sid, deviceId, _) =>
          if (stations.contains(sid)) {
            sharded(TemperatureDevice.TypeKey, deviceId) ! cmd
          }

          Behaviors.same
      }
    }

}
