package sample.sharding

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors

/**
 * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 */
object Device {

  sealed trait Command extends Message

  final case class RecordTemperature(deviceId: Int, temperature: Double) extends Command

  final case class GetTemperature(deviceId: Int, replyTo: ActorRef[Message]) extends Command

  final case class Temperature(deviceId: Int, average: Double, latest: Double, readings: Int) extends Message {
    override def toString: String =
      s"Temperature[device=$deviceId, temperature=$latest, average=$average, readings=$readings]"
  }

  object Temperature {

    def apply(id: Int, values: Vector[Double]): Temperature =
      if (values.isEmpty)
        Temperature(id, Double.NaN, Double.NaN, 0)
      else
        Temperature(id, average(values), values.last, values.size)

    def average(values: Vector[Double]): Double =
      if (values.isEmpty) Double.NaN
      else values.sum / values.size

  }

  def apply(entityId: String): Behavior[Message] = {

    def counting(values: Vector[Double]): Behavior[Message] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage[Message] {
          case RecordTemperature(id, temp) =>
            val temperatures = values :+ temp
            context.log.info(
              s"Recording temperature $temp for device $id, average is ${Temperature.average(temperatures)} after " +
              s"${temperatures.size} readings")

            counting(temperatures)

          case GetTemperature(id, replyTo) =>
            replyTo ! Temperature(id, values)
            Behaviors.same

        }
      }

    counting(Vector.empty)
  }

}
