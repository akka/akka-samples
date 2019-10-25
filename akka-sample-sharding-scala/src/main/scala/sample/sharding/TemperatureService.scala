package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.{ ClusterShardingSettings, ShardingEnvelope }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.cluster.typed.{ Cluster, Join }

object TemperatureService {

  // Update a random device
  case object UpdateDevice extends Message
  case object ReadTemperatures extends Message

  val TypeKey: EntityTypeKey[Message] =
    EntityTypeKey[Message]("Device")

  def apply(): Behavior[Message] =
    Behaviors.setup { context =>
      val cluster = Cluster(context.system)
      cluster.manager ! Join(cluster.selfMember.address)

      val numberOfDevices = ClusterShardingSettings(context.system).numberOfShards
      val random = new Random()

      Behaviors.withTimers { timer =>
        val deviceRegion: ActorRef[ShardingEnvelope[Message]] =
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
            deviceRegion ! ShardingEnvelope(deviceId.toString, event)

            Behaviors.same

          case ReadTemperatures =>
            (0 to numberOfDevices).foreach { deviceId =>
              // alternatively, you can send via the entity directly, without the ShardingEnvelope
              val entityRef = ClusterSharding(context.system).entityRefFor(TypeKey, deviceId.toString)
              entityRef ! Device.GetTemperature(deviceId, context.self)
            }
            Behaviors.same

          case temp: Device.Temperature =>
            if (temp.readings > 0)
              context.log.info("{}", temp)

            Behaviors.same
        }
      }
    }
}
