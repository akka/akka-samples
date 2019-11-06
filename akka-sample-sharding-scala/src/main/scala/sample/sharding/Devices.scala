package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor._
import akka.cluster.sharding._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.pattern.extended.ask // note extended.ask
import akka.pattern.pipe
import akka.util.Timeout

object Devices {
  // Update a random device
  case object UpdateDevice

  case object ReadTemperatures

  def props(): Props =
    Props(new Devices)
}

class Devices extends Actor with ActorLogging with Timers {
  import Devices._

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Device.RecordTemperature(id, _) => (id.toString, msg)
    case msg @ Device.GetTemperature(id, _)    => (id.toString, msg)
    case ShardingEnvelope(_, msg @ Device.RecordTemperature(id, _)) =>
      (id.toString, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case Device.RecordTemperature(id, _) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    case Device.GetTemperature(id, _) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    case ShardRegion.StartEntity(id) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    case ShardingEnvelope(_, Device.RecordTemperature(id, _)) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    case ShardingEnvelope(_, Device.GetTemperature(id, _)) =>
      (math.abs(id.hashCode) % numberOfShards).toString
  }

  val deviceRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = "Device",
    entityProps = Device.props(),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  val random = new Random()
  val numberOfDevices = 50

  timers.startTimerWithFixedDelay(UpdateDevice, UpdateDevice, 1.second)
  timers.startTimerWithFixedDelay(
    ReadTemperatures,
    ReadTemperatures,
    15.seconds
  )

  def receive = {
    case UpdateDevice =>
      val deviceId = random.nextInt(numberOfDevices)
      val temperature = 5 + 30 * random.nextDouble()
      val msg = Device.RecordTemperature(deviceId, temperature)
      log.info(s"Sending $msg")
      deviceRegion ! msg

    case ReadTemperatures =>
      (0 to numberOfDevices).foreach { deviceId =>
        if (deviceId >= 40) {
          import context.dispatcher
          implicit val timeout = Timeout(3.seconds)
          deviceRegion
            .ask(replyTo => Device.GetTemperature(deviceId, replyTo))
            .pipeTo(self)
        } else
          deviceRegion ! Device.GetTemperature(deviceId, self)
      }

    case temp: Device.Temperature =>
      if (temp.readings > 0)
        log.info(
          "Temperature of device {} is {} with average {} after {} readings",
          temp.deviceId,
          temp.latest,
          temp.average,
          temp.readings
        )
  }
}
