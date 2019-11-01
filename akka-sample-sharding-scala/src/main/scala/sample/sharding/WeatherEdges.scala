package sample.sharding

import scala.concurrent.duration._
import scala.util.{ Failure, Random, Success }

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.{ Behaviors, TimerScheduler }
import com.typesafe.config.ConfigFactory

/** Simulate devices and stations.
 * In the wild, each station would run its own simple system,
 * taking samples and posting via HTTPS.
 */
object WeatherEdges {

  private val WeatherStations = 50

  // temperature, pressure, precipitation, wind speed...
  private val DevicesPerStation = 1

  val random = new Random()

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load("weather-station.conf")

    // simple sample, using one node on local machine
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      (0 to WeatherStations).foreach { sid =>
        context.spawn(Station(sid, DevicesPerStation, 8081), s"station-$sid")
      }
      Behaviors.empty
    }

    ActorSystem[Nothing](rootBehavior, "SimulatedDevices", config)
  }
}

object Station {

  def apply(stationId: Int, devices: Int, port: Int): Behavior[WeatherHttpApi.Data] =
    Behaviors.setup { context =>
      context.log.info("Started station with {} devices.", devices)
      val http = context.spawn(WeatherHttpApi(stationId, port), s"http-post-$stationId")

      (0 to devices).foreach { deviceId =>
        context.spawn(WeatherDevice(stationId, deviceId, http, 1.second, 10), s"device-$deviceId")
      }

      Behaviors.empty
    }
}

/**
 * Any given weather station has a unique ID, and can sample temperature, wind speed, precipitation, etc.
 * from multiple devices.
 *
 * For a simple example, this does one type of reading.
 */
object WeatherHttpApi {

  final case class Data(stationId: Int, deviceId: Int, data: List[Double])

  // This formatter determines how to convert to and from Data objects:
  import spray.json._
  import spray.json.DefaultJsonProtocol._
  implicit val dataFormat: RootJsonFormat[Data] = jsonFormat3(Data)

  def apply(stationId: Int, port: Int): Behavior[Data] =
    Behaviors.setup[Data] { context =>
      import akka.http.scaladsl.Http
      import akka.http.scaladsl.model.HttpRequest

      implicit val sys = context.system
      import context.executionContext
      import akka.actor.typed.scaladsl.adapter._
      val http = Http(context.system.toClassic)

      // Run and completely consume a single akka http request
      def runRequest(req: HttpRequest) =
        http.singleRequest(req).flatMap { _.entity.dataBytes.runReduce(_ ++ _) }

      // This import makes the 'format' above available to the Akka HTTP
      // marshalling infractructure used when constructing the Post below:
      import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

      import akka.http.scaladsl.client.RequestBuilding.Post

      Behaviors.receiveMessage[Data] {
        case data: Data =>
          runRequest(Post(s"http://localhost:$port/temperatures", data)).onComplete {
            case Success(response) =>
              context.log.info(response.utf8String)
            case Failure(e) => context.log.error("Something wrong.", e)
          }

          Behaviors.same
      }
    }
}

object WeatherDevice {
  case object Tick
  private case object TimerKey

  def apply(
      stationId: Int,
      deviceId: Int,
      target: ActorRef[WeatherHttpApi.Data],
      after: FiniteDuration,
      maxSize: Int): Behavior[WeatherDevice.Tick.type] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(TimerKey, Tick, after)
      new WeatherDevice(stationId, deviceId, timers, target, after, maxSize).active(Nil)
    }
  }
}

class WeatherDevice(
    stationId: Int,
    deviceId: Int,
    timers: TimerScheduler[WeatherDevice.Tick.type],
    target: ActorRef[WeatherHttpApi.Data],
    after: FiniteDuration,
    maxSize: Int) {

  import WeatherDevice._

  def active(buffer: List[Double]): Behavior[WeatherDevice.Tick.type] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage[WeatherDevice.Tick.type] {
        case Tick =>
          val value = 5 + 30 * WeatherEdges.random.nextDouble()
          val newBuffer = buffer :+ value
          context.log.info("Added device reading: {}", value)

          if (newBuffer.size == maxSize) {
            context.log.info("Sending {} buffered events to {}.", buffer.size, target.path)
            target ! WeatherHttpApi.Data(stationId, deviceId, newBuffer)
            active(Nil)
          } else {
            active(newBuffer)
          }
      }
    }
  }
}
