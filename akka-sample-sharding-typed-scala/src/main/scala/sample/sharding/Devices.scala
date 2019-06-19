package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

object Devices {
  sealed trait Command

  private case object UpdateDevice extends Command

  private case object ReadTemperatures extends Command

  private case class GetTemperatureReply(temp: Device.Temperature)
      extends Command

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      Device.init(context.system)
      val sharding = ClusterSharding(context.system)

      Behaviors.withTimers { timers =>
        val random = new Random()
        val numberOfDevices = 50

        timers.startTimerWithFixedDelay(UpdateDevice, UpdateDevice, 1.second)
        timers.startTimerWithFixedDelay(
          ReadTemperatures,
          ReadTemperatures,
          15.seconds
        )

        val temperatureAdapter =
          context.messageAdapter[Device.Temperature](GetTemperatureReply(_))

        Behaviors.receiveMessage {
          case UpdateDevice =>
            val deviceId = random.nextInt(numberOfDevices)
            val temperature = 5 + 30 * random.nextDouble()
            val msg = Device.RecordTemperature(deviceId, temperature)
            context.log.info(s"Sending $msg")
            sharding.entityRefFor(Device.TypeKey, deviceId.toString) ! msg
            Behaviors.same

          case ReadTemperatures =>
            (0 to numberOfDevices).foreach { deviceId =>
              val entityRef =
                sharding.entityRefFor(Device.TypeKey, deviceId.toString)
              entityRef ! Device.GetTemperature(deviceId, temperatureAdapter)
            }
            Behaviors.same

          case GetTemperatureReply(temp: Device.Temperature) =>
            if (temp.readings > 0)
              context.log.info(
                "Temperature of device {} is {} with average {} after {} readings",
                temp.deviceId,
                temp.latest,
                temp.average,
                temp.readings
              )
            Behaviors.same
        }
      }
    }
  }
}
