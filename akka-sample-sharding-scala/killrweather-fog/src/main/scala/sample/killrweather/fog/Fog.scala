package sample.killrweather.fog

import java.net.{DatagramSocket, InetSocketAddress}
import java.nio.channels.DatagramChannel

import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.Config

/**
  * In another terminal start the `Fog` (see Fog computing https://en.wikipedia.org/wiki/Fog_computing).
  * Starts the fog network, simulating devices and stations.
  * In the wild, each station would run its own system and be location-aware.
  */
object Fog {

  def main(args: Array[String]): Unit = {

    val ports = if (args.isEmpty) Seq(8081) else args.map(_.toInt).toSeq

    ActorSystem[Nothing](Guardian(ports.toVector), "Fog")
  }
}

object Guardian {

  def apply(ports: Vector[Int]): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val settings = FogSettings(context.system)
      val weatherPorts = ports.flatMap(settings.findHttpPort)

      (1 until settings.weatherStations).foreach { n =>
        val wsid = WeatherStation.WmoId(n.toString)
        val weatherPort = weatherPorts(n % weatherPorts.size)

        context.spawn(WeatherStation(wsid, settings, weatherPort), s"weather-station-${wsid.id}")
      }
      Behaviors.empty
    }
  }
}

object FogSettings {

  def apply(system: ActorSystem[_]): FogSettings = {
    apply(system.settings.config.getConfig("killrweather.fog"))
  }

  def apply(config: Config): FogSettings = {
    import akka.util.Helpers.Requiring

    val millis = (durationKey: String) =>
      config.getDuration(durationKey).toMillis.millis
        .requiring(_ > Duration.Zero, s"'$durationKey' must be > 0")

    FogSettings(
      config,
      weatherStations =  config.getInt("initial-weather-stations"),
      staggerStartup = millis("stagger-startup"),
      host = config.getString("weather-station.hostname"),
      registrationTimeout = millis("weather-station.registration-timeout"),
      sampleInterval = millis("weather-station.sample-interval")
    )
  }
}

final case class FogSettings(
    config: Config,
    weatherStations: Int,
    staggerStartup: FiniteDuration,
    host: String,
    registrationTimeout: FiniteDuration,
    sampleInterval: FiniteDuration) {

  def findHttpPort(attempt: Int): Option[Int] = {
    val ds: DatagramSocket = DatagramChannel.open().socket()
    try {
      ds.bind(new InetSocketAddress(host, attempt))
      Some(attempt)
    } catch {
      case NonFatal(e) =>
        ds.close()
        println(s"Unable to bind to port $attempt for http server to send data: ${e.getMessage}")
        None
    } finally
      ds.close()
  }
}
