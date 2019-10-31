package sample.sharding

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }

/** Base aggregator behavior to compute any implementation of data aggregation. */
trait Aggregator {
  import Protocol._

  protected def aggregating(id: String, values: Vector[Double]): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Starting sharded aggregator {}.", id)

      Behaviors.receiveMessage {
        case UpdateDevice(sid, did, data) =>
          val updated = values ++ data.toVector
          context.log.info(
            s"Recording latest $data for device $did from station $sid, average is ${Aggregate.average(updated)} after " +
            s"${updated.size} readings.")

          aggregating(id, updated)

        case GetAggregate(sid, did, replyTo) =>
          context.log.info("Sending aggregate for device {}.", id)
          replyTo ! Aggregate(sid, did, values)
          Behaviors.same

        case GetHiLow(sid, did, replyTo) =>
          context.log.info("Sending HiLow for device {}.", id)
          replyTo ! HighLow(sid, did, values.min, values.max, values.size)
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
  import Protocol._

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Temperature")

  def init(system: ActorSystem[_]): Unit = {
    ClusterSharding(system).init(Entity(TypeKey)(entityContext => TemperatureDevice(entityContext.entityId)))
  }

  def apply(id: String): Behavior[Command] = aggregating(id, Vector.empty)

}
