package sample.sharding

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

/**
  * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
  * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
  * so you need to distribute them across several nodes.
  */
object Device {
  val TypeKey = EntityTypeKey[Device.Command]("Device")

  def init(system: ActorSystem[_]): Unit = {

    // If the original hashing function was using
    // `(math.abs(id.hashCode) % numberOfShards).toString`
    // the default HashCodeMessageExtractor in Typed can be used.
    // That is also compatible with `akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor`.
    ClusterSharding(system).init(Entity(TypeKey, _ => Device()))
  }

  sealed trait Command extends Message

  case class RecordTemperature(deviceId: Int, temperature: Double)
      extends Command

  case class GetTemperature(deviceId: Int, replyTo: ActorRef[Temperature])
      extends Command

  case class Temperature(deviceId: Int,
                         average: Double,
                         latest: Double,
                         readings: Int)
      extends Message

  def apply(): Behavior[Command] =
    counting(Vector.empty)

  private def counting(values: Vector[Double]): Behavior[Command] = {
    Behaviors.receive { (context, cmd) =>
      cmd match {
        case RecordTemperature(id, temp) =>
          val temperatures = values :+ temp
          context.log.info(
            s"Recording temperature $temp for device $id, average is ${average(temperatures)} after " +
              s"${temperatures.size} readings"
          )
          counting(temperatures)

        case GetTemperature(id, replyTo) =>
          val reply =
            if (values.isEmpty)
              Temperature(id, Double.NaN, Double.NaN, 0)
            else
              Temperature(id, average(values), values.last, values.size)
          replyTo ! reply
          Behaviors.same
      }
    }
  }

  private def average(values: Vector[Double]): Double =
    if (values.isEmpty) Double.NaN
    else values.sum / values.size
}
