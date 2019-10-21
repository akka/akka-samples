package sample.sharding

import scala.concurrent.duration._
import scala.util.Random

import akka.actor._
import akka.cluster.sharding._

object Devices {
  // Update a random device
  case object UpdateDevice

  case object ReadTemperatures

  case object Start

  def props(): Props =
    Props(new Devices)
}

class Devices extends Actor with ActorLogging with Timers {
  import Devices._

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Device.RecordTemperature(id, _,_, _) => (id.toString, msg)
    case msg @ Device.GetTemperature(id)       => (id.toString, msg)
  }

  private val numberOfShards = 10

  private val extractShardId: ShardRegion.ExtractShardId = {
    case Device.RecordTemperature(id, _, _, _) =>
      (math.abs(id.hashCode) % numberOfShards).toString
    case Device.GetTemperature(id) =>
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
  val numberOfDevices = 10

  if (context.system.settings.config.getBoolean("sample.sending-temperatures")) {
    timers.startSingleTimer(Start, Start, 20.seconds)
  }

  private var seqNr = 0L
  private var sequenceNumbers = Map.empty[Long, Int]

  def receive = {
    case Start =>
      timers.startTimerWithFixedDelay(UpdateDevice, UpdateDevice, 100.millis)
      timers.startTimerWithFixedDelay(
        ReadTemperatures,
        ReadTemperatures,
        15.seconds
      )

    case UpdateDevice =>
      seqNr += 1
      //val deviceId = random.nextInt(numberOfDevices)
      val deviceId = (seqNr % numberOfDevices).toInt
      sequenceNumbers = sequenceNumbers.updated(seqNr, deviceId)
      val temperature = 5 + 30 * random.nextDouble()
      val msg = Device.RecordTemperature(deviceId, temperature, System.nanoTime(), seqNr)
      log.info(s"Sending $msg")
      deviceRegion ! msg

    case Device.RecordTemperatureAck(deviceId, startTime, seqNr) =>
      val durationMs = (System.nanoTime() - startTime) / 1000 / 1000
      if (durationMs > 500)
        log.info("Delayed ack of device {} seqNr {} after {} ms", deviceId, seqNr, durationMs)
      else
        log.info("Ack of device {} seqNr {} after {} ms", deviceId, seqNr, durationMs)

      sequenceNumbers -= seqNr
      log.info("Pending sequence numbers: {}", sequenceNumbers)

    case ReadTemperatures =>
      (0 to numberOfDevices).foreach { deviceId =>
        deviceRegion ! Device.GetTemperature(deviceId)
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
