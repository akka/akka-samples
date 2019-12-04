package sample.killrweather

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

private[killrweather] object Aggregator {
  sealed trait Command extends CborSerializable

  sealed trait DataType {
    def tag: String = getClass.getSimpleName
  }
  object DataType {
    case object Temperature extends DataType
    case object Dewpoint extends DataType
    case object Pressure extends DataType
    case object WindDirection extends DataType
    case object OneHourPrecip extends DataType
    case object SixHourPrecip extends DataType
    case object SkyCondition extends DataType
  }

  sealed trait Function {
    def tag: String = getClass.getSimpleName
  }
  object Function {
    case object HighLow extends Function
    case object Average extends Function
    case object Current extends Function
  }

  /** Actual weather data event comprises many more data points.
   *
   * @param wsid maps to the Cluster Sharding `EntityTypeKey`
   * @param eventTime time collected
   * @param temperature Air temperature (degrees Celsius)
   */
  final case class Data(wsid: String, eventTime: Long, temperature: Double)
  final case class Aggregate(data: Data, processingTimestamp: Long, replyTo: ActorRef[WeatherRoutes.DataIngested]) extends Command
  final case class Query(wsid: String, dataType: Aggregator.DataType, func: Aggregator.Function, replyTo: ActorRef[WeatherRoutes.QueryResult]) extends Command

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
  import WeatherRoutes.{QueryResult, TimeWindow}

  def TypeKey: EntityTypeKey[Aggregator.Command]

  protected def aggregating(entityId: String, values: Vector[Data]): Behavior[Aggregator.Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Aggregate(data, received, replyTo) =>
          val updated = values :+ data
          context.log.debug(
            s"${updated.size} total readings from station ${entityId}, " +
              s"average ${average(updated.map(_.temperature))}, diff: processingTime - eventTime: ${received - data.eventTime}ms")

          replyTo ! WeatherRoutes.DataIngested(data.wsid)

          aggregating(entityId, updated) // store

        case Query(wsid, dataType, func, replyTo) =>
          // typically you would separate read/write, here for simplicity we show both
          val value = dataType match {
            case DataType.Temperature =>
              func match {
                case Function.Average =>
                  val start: Long = values.headOption.map(_.eventTime).getOrElse(0)
                  val end: Long = values.lastOption.map(_.eventTime).getOrElse(0)
                  Vector(TimeWindow(start, end, average(values.map(_.temperature))))

                case Function.HighLow =>
                  val (start, min) = values.map(e => e.eventTime -> e.temperature).min
                  val (end, max) = values.map(e => e.eventTime -> e.temperature).max
                  Vector(TimeWindow(start, end, min), TimeWindow(start, end, max))

                case Function.Current =>
                  Vector(values.lastOption
                    .map(e => TimeWindow(e.eventTime, e.eventTime, e.temperature))
                    .getOrElse(TimeWindow(0, 0, 0.0)))
              }

            case _ => ???
          }

          replyTo ! QueryResult(wsid, dataType.tag, func.tag, values.size, value)

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
