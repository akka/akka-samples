package sample.sharding

import scala.concurrent.duration._
import scala.util.{ Failure, Random, Success }

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, PostStop }
import akka.actor.typed.scaladsl.{ Behaviors, TimerScheduler }
import com.typesafe.config.ConfigFactory

/** Simulate devices and stations.
 * In the wild, each station would run its own simple system,
 * taking samples and posting via HTTPS.
 */
object EdgesApp {

  val WeatherStations = 50
  // temperature, pressure, precipitation, wind speed...
  val DevicesPerStation = 1

  val random = new Random()

  def main(args: Array[String]): Unit = {
    val config =
      ConfigFactory
        .parseString(s"akka.remote.artery.canonical.port = 0")
        .withFallback(ConfigFactory.load("weather-station.conf"))

    // simple sample, using one node on local machine
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      (0 to WeatherStations).foreach { sid =>
        context.spawn(Station(sid, DevicesPerStation, 8080), s"station-$sid")
      }

      Behaviors.empty
    }

    ActorSystem[Nothing](rootBehavior, "SimulatedDevices", config)
  }
}

object Station {

  def apply(stationId: Int, devices: Int, port: Int): Behavior[Device.Data] =
    Behaviors.setup { context =>
      context.log.info("Started station with {} devices.", devices)
      val http = context.spawn[Device.Data](Post(stationId, port), s"http-post-$stationId")

      (0 to devices).foreach { deviceId =>
        context.spawn(Device(stationId, deviceId, http, 5.second, 10), s"device-$deviceId")
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
object Post {

  def apply(stationId: Int, port: Int): Behavior[Device.Data] =
    Behaviors.setup[Device.Data] { context =>
      import akka.http.scaladsl.Http
      import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpMethods, HttpRequest }
      import context.executionContext

      import akka.actor.typed.scaladsl.adapter._
      val http = Http(context.system.toClassic)

      Behaviors.receiveMessage[Device.Data] {
        case Device.Data(sid, did, data) =>
          // TODO
          val values = data.mkString(",")
          val json = s"""{"stationId":$sid,"deviceId":$did,"data":[$values]}"""
          context.log.info("Read: {}", json)

          // See https://doc.akka.io/docs/akka-http/current/common/index.html
          // for marshalling, unmarshalling, json support in the wild.
          val httpEntity = HttpEntity(ContentTypes.`application/json`, json)

          http
            .singleRequest(
              HttpRequest(method = HttpMethods.POST, uri = s"http://localhost:$port/temperature", entity = httpEntity))
            .onComplete {
              case Success(res) => context.log.info(res.toString())
              case Failure(e)   => context.log.error("Something wrong.", e)
            }

          Behaviors.same
      }
    }
}

object Device {
  sealed trait Command
  private case object Tick extends Command
  final case class Data(stationId: Int, deviceId: Int, data: Vector[Double]) extends Command
  case object Stop extends Command
  private case object TimerKey

  def apply(
      stationId: Int,
      deviceId: Int,
      target: ActorRef[Data],
      after: FiniteDuration,
      maxSize: Int): Behavior[Command] = {
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(TimerKey, Tick, after)
      new Device(stationId, deviceId, timers, target, after, maxSize).active(Vector.empty)
    }
  }
}

class Device(
    stationId: Int,
    deviceId: Int,
    timers: TimerScheduler[Device.Command],
    target: ActorRef[Device.Data],
    after: FiniteDuration,
    maxSize: Int) {

  import Device._

  def active(buffer: Vector[Double]): Behavior[Device.Command] = {
    Behaviors.setup { context =>
      Behaviors
        .receiveMessage[Device.Command] {
          case Tick =>
            val value = 5 + 30 * EdgesApp.random.nextDouble()
            val newBuffer = buffer :+ value
            context.log.info("Added device reading: {}", value)

            if (newBuffer.size == maxSize) {
              context.log.info("Sending {} buffered events to {}.", buffer.size, target.path)
              target ! Data(stationId, deviceId, newBuffer)
              active(Vector.empty)
            } else {
              active(newBuffer)
            }
          case _ => Behaviors.unhandled
        }
        .receiveSignal {
          case (context, PostStop) =>
            timers.cancel(TimerKey)
            target ! Data(stationId, deviceId, buffer)
            context.log.info("Device shutting down.")

            Behaviors.same
        }
    }
  }
}
