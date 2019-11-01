package sample.sharding

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }

object Aggregator {

  sealed trait Command
  final case class Aggregate(stationId: Int, deviceId: Int, data: List[Double]) extends Command
  final case class GetAverage(stationId: Int, deviceId: Int, replyTo: ActorRef[Guardian.AggregateData]) extends Command
  final case class GetHiLow(stationId: Int, deviceId: Int, replyTo: ActorRef[Guardian.AggregateData]) extends Command

  private def average(values: List[Double]): Double =
    if (values.isEmpty) Double.NaN
    else values.sum / values.size
}

/** Base aggregator behavior to compute any implementation of data aggregation. */
trait Aggregator {

  import Aggregator._

  protected def aggregating(id: String, values: List[Double]): Behavior[Aggregator.Command] =
    Behaviors.setup { context =>
      context.log.info("Starting sharded aggregator {}.", id)

      Behaviors.receiveMessage {
        case Aggregate(sid, did, data) =>
          val updated = data ::: values
          context.log.info(
            s"Recording latest data points ${data.size} for device $did from station $sid, average is ${average(updated)} after " +
            s"${updated.size} readings.")

          aggregating(id, updated)

        case GetAverage(sid, did, replyTo) =>
          context.log.info("Sending aggregate for device {}.", id)
          replyTo ! Guardian.Average(sid, did, average(values), values.size)
          Behaviors.same

        case GetHiLow(sid, did, replyTo) =>
          context.log.info("Sending HiLow for device {}.", id)
          replyTo ! Guardian.HighLow(sid, did, values.min, values.max, values.size)
          Behaviors.same

      }
    }
}

/**
 * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 */
object TemperatureDevice extends Aggregator {

  val TypeKey: EntityTypeKey[Aggregator.Command] =
    EntityTypeKey[Aggregator.Command]("Temperature")

  def init(system: ActorSystem[_]): Unit = {
    ClusterSharding(system).init(Entity(TypeKey)(entityContext => TemperatureDevice(entityContext.entityId)))
  }

  def apply(id: String): Behavior[Aggregator.Command] = aggregating(id, Nil)

}
