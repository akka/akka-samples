package sample.killrweather.fog

import java.net.{ DatagramSocket, InetSocketAddress }
import java.nio.channels.DatagramChannel

import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }

/**
  * In another terminal start the `Fog` (see Fog computing https://en.wikipedia.org/wiki/Fog_computing).
  * Starts the fog network, simulating devices and stations.
  * In the wild, each station would run its own system and be location-aware.
  */
object Fog {

  def main(args: Array[String]): Unit = {
    val ports = if (args.isEmpty) Seq(8081) else args.map(_.toInt).toSeq
    val weatherPorts = ports.flatMap(findHttpPort).toVector

    val settings = FogSettings()

    // simple sample, using one node on local machine
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      (1 until settings.weatherStations).foreach { n =>
        val wsid = WeatherStation.WmoId(n.toString)
        val weatherPort = weatherPorts(n % weatherPorts.size)

        context.spawn(WeatherStation(wsid, settings, weatherPort), s"weather-station-${wsid.id}")

        // for sample reasons only
        Thread.sleep(settings.staggerStartup.toMillis)
      }
      Behaviors.empty
    }

    ActorSystem[Nothing](rootBehavior, "Fog", settings.config)
  }

  private def findHttpPort(attempt: Int): Option[Int] = {
    val ds: DatagramSocket = DatagramChannel.open().socket()
    try {
      ds.bind(new InetSocketAddress("localhost", attempt))
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

object FogSettings {

  def apply(): FogSettings = {
    import akka.util.Helpers.Requiring

    val config =
      ConfigFactory.load("application.conf").getConfig("killrweather")

    FogSettings(
      config,
      weatherStations =  config.getInt("weather-stations"),
      staggerStartup = {
        config.getDuration("stagger-startup").toMillis.millis
        }.requiring(_ > Duration.Zero, "stagger-startup must be > 0"),
      sampleInterval = {
        config.getDuration("sample-interval").toMillis.millis
        }.requiring(_ > Duration.Zero, "'sample-interval' must be > 0")
    )
  }
}

final case class FogSettings(
    config: Config,
    weatherStations: Int,
    staggerStartup: FiniteDuration,
    sampleInterval: FiniteDuration
)
