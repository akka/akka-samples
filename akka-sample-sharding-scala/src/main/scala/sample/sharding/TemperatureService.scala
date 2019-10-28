package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }

object TemperatureService {

  trait TemperatureEvent
  // Update a random device
  case object UpdateDevice extends TemperatureEvent
  case object ReadTemperatures extends TemperatureEvent

  val TypeKey: EntityTypeKey[Device.Command] =
    EntityTypeKey[Device.Command]("Device")

  def apply(): Behavior[TemperatureEvent] =
    Behaviors.setup { context =>

      val random = new Random()
      val numberOfDevices = 50

      Behaviors.withTimers { timer =>
        val temperatureRegion: ActorRef[ShardingEnvelope[Device.Command]] =
          ClusterSharding(context.system).init(Entity(TypeKey)(createBehavior = entityContext =>
            Device(entityContext.entityId)))

        timer.startTimerWithFixedDelay(UpdateDevice, UpdateDevice, 1.second)
        timer.startTimerWithFixedDelay(ReadTemperatures, ReadTemperatures, 15.seconds)

        Behaviors.receiveMessage {
          case UpdateDevice =>
            val deviceId = random.nextInt(numberOfDevices)
            val temperature = 5 + 30 * random.nextDouble()
            val event = Device.RecordTemperature(deviceId, temperature)
            context.log.info("Sending {}.", event)

            // send to the region with the ShardingEnvelope and entityId
            temperatureRegion ! ShardingEnvelope(deviceId.toString, event)

            Behaviors.same

          case ReadTemperatures =>
            (0 to numberOfDevices).foreach { deviceId =>
              // alternatively, you can send via the entity directly, without the ShardingEnvelope
              val entityRef = ClusterSharding(context.system).entityRefFor(TypeKey, deviceId.toString)
              entityRef ! Device.GetTemperature(deviceId, context.self)
            }
            Behaviors.same

          case Device.Temperature(deviceId, latest, average, readings) =>
            if (readings > 0)
              context.log.info(
                s"Temperature[device=$deviceId, temperature=$latest, average=$average, readings=$readings]")

            Behaviors.same
        }
      }
    }
}
