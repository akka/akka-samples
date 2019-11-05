package sample.killrweather

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

private[killrweather] object Aggregator {
  sealed trait Command extends CborSerializable

  sealed trait DataType
  object DataType {
    case object Temperature extends DataType
    case object Dewpoint extends DataType
    case object Pressure extends DataType
    case object WindDirection extends DataType
    case object OneHourPrecip extends DataType
    case object SixHourPrecip extends DataType
    case object SkyCondition extends DataType
  }

  sealed trait Function
  object Function {
    case object HighLow extends Function
    case object Average extends Function
  }

  /** Actual weather data event comprises many more data points.
   *
   * @param wsid maps to the Cluster Sharding `EntityTypeKey`
   * @param eventTime time collected
   * @param temperature Air temperature (degrees Celsius)
   */
  final case class Data(wsid: String, eventTime: Long, temperature: Double)
  final case class Aggregate(data: Data, processingTimestamp: Long) extends Command
  // TODO time window option
  final case class Get(wsid: String, `type`: Aggregator.DataType, func: Aggregator.Function, replyTo: ActorRef[Guardian.AggregateData]) extends Command

  private def average(values: Vector[Double]): Double =
    if (values.isEmpty) Double.NaN
    else values.sum / values.size
}

/** Base aggregator behavior for data computation by sharded data type.
 *
 * A sharded `Aggregator` has a declared data type and receives that data stream
 * from remote devices via the  `Guardian`. For each `Aggregator`, for one or
 * across all weather stations, common cumulative computations can be run
 * for a given time window queried, e.g. daily, monthly or annual such as:
 * aggregate, averages, high/low, topK (e.g. the top N highest temperatures).
 */
private[killrweather] trait Aggregator {

  import Aggregator._

  def TypeKey: EntityTypeKey[Aggregator.Command]

  protected def aggregating(entityId: String, values: Vector[Data]): Behavior[Aggregator.Command] =
    Behaviors.setup { context =>
      context.log.info("Starting sharded aggregator {}.", entityId)

      Behaviors.receiveMessage {
        case Aggregate(data, received) =>
          val updated = values :+ data
          context.log.info(
            s"Recorded latest ${updated.size} data from station ${entityId}, received at $received, " +
              s"average is ${average(updated.map(_.temperature))} after ${updated.size} readings.")

          aggregating(entityId, updated) // TODO store

        case Get(wsid, dataType, func, replyTo) =>
          dataType match {
            case DataType.Temperature =>
              func match {
                case Function.Average =>
                  replyTo ! Guardian.Average(wsid, dataType, average(values.map(_.temperature)), values.size)
                case Function.HighLow =>
                  replyTo ! Guardian.HighLow(wsid, dataType, values.map(_.temperature).min, values.map(_.temperature).max, values.size)
                case _ => ???
              }

            case _ => ???
          }

          Behaviors.same
      }
    }
}

/**
 * Generally geo-based data, unless it is a floating station.
 *
 * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 */
private[killrweather] object WeatherAggregator extends Aggregator {

  val TypeKey: EntityTypeKey[Aggregator.Command] =
    EntityTypeKey[Aggregator.Command]("WeatherStation")

  def init(system: ActorSystem[_]): Unit =
    ClusterSharding(system).init(Entity(TypeKey) { entityContext =>
      WeatherAggregator(entityContext.entityId)
    })

  def apply(id: String): Behavior[Aggregator.Command] = aggregating(id, Vector.empty)

}
