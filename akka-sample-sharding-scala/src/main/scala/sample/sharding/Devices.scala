package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor._
import akka.cluster.sharding._

object Devices {
  // Update a random device
  case object UpdateDevice

  def props(): Props =
    Props(new Devices)
}

class Devices extends Actor with ActorLogging with Timers {
  import Devices._

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Device.RecordTemperature(id, _) => (id.toString, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case Device.RecordTemperature(id, _) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    case ShardRegion.StartEntity(id) =>
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

  def receive = {
    case UpdateDevice =>
      val deviceId = random.nextInt(numberOfDevices)
      val temperature = 5 + 30 * random.nextDouble()
      val msg = Device.RecordTemperature(deviceId, temperature)
      log.info(s"Sending $msg")
      deviceRegion ! msg
  }
}
