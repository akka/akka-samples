package sample.sharding

import akka.actor._

/**
  * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
  * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
  * so you need to distribute them across several nodes.
  */
object Device {
  sealed trait Command extends Message

  case class RecordTemperature(deviceId: Int, temperature: Double)
      extends Command

  case class GetTemperature(deviceId: Int, replyTo: ActorRef) extends Command

  case class Temperature(deviceId: Int,
                         average: Double,
                         latest: Double,
                         readings: Int)
      extends Message

  def props(): Props =
    Props(new Device)
}
class Device extends Actor with ActorLogging {
  import Device._

  override def receive = counting(Vector.empty)

  def counting(values: Vector[Double]): Receive = {
    case RecordTemperature(id, temp) =>
      val temperatures = values :+ temp
      log.info(
        s"Recording temperature $temp for device $id, average is ${average(temperatures)} after " +
          s"${temperatures.size} readings"
      )
      context.become(counting(temperatures))

    case GetTemperature(id, replyTo) =>
      val reply =
        if (values.isEmpty)
          Temperature(id, Double.NaN, Double.NaN, 0)
        else
          Temperature(id, average(values), values.last, values.size)

      if (replyTo == null)
        sender() ! reply
      else
        replyTo ! reply

  }

  private def average(values: Vector[Double]): Double =
    if (values.isEmpty) Double.NaN
    else values.sum / values.size
}
