package sample.killrweather

import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.actor.typed.scaladsl.LoggerOps

/**
 * Generally geo-based data, unless it is a floating station.
 *
 * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 *
 * A sharded `WeatherStation` has a set of recorded datapoints
 * For each weather station common cumulative computations can be run:
 * aggregate, averages, high/low, topK (e.g. the top N highest temperatures).
 *
 * Note that since this station is not persistent, if Akka Cluster Sharding rebalances it - moves it to another
 * node because of cluster nodes added removed etc - it will loose all its state.
 */
private[killrweather] object WeatherStation {

  // setup for using WeatherStations through Akka Cluster Sharding
  // these could also live elsewhere and the WeatherStation class be completely
  // oblivious to being used in sharding
  val TypeKey: EntityTypeKey[WeatherStation.Command] =
    EntityTypeKey[WeatherStation.Command]("WeatherStation")

  def initSharding(system: ActorSystem[_]): Unit =
    ClusterSharding(system).init(Entity(TypeKey) { entityContext =>
      WeatherStation(entityContext.entityId)
    })


  // actor commands and responses
  sealed trait Command extends CborSerializable

  final case class Record(data: Data, processingTimestamp: Long, replyTo: ActorRef[DataRecorded]) extends Command
  final case class DataRecorded(wsid: String) extends CborSerializable

  final case class Query(wsid: String, dataType: DataType, func: Function, replyTo: ActorRef[QueryResult]) extends Command
  final case class QueryResult(wsid: String, dataType: DataType, func: Function, readings: Int, value: Vector[TimeWindow]) extends CborSerializable

  // small domain model for queriying and storing weather data
  sealed trait DataType
  object DataType {
    /** Temperature in celcius */
    case object Temperature extends DataType
    case object Dewpoint extends DataType
    case object Pressure extends DataType
    case object WindDirection extends DataType
    case object OneHourPrecip extends DataType
    case object SixHourPrecip extends DataType
    case object SkyCondition extends DataType
    val All: Set[DataType] = Set(Temperature, Dewpoint, Pressure, WindDirection, OneHourPrecip, SixHourPrecip, SkyCondition)
  }

  sealed trait Function
  object Function {
    case object HighLow extends Function
    case object Average extends Function
    case object Current extends Function
    val All: Set[Function] = Set(HighLow, Average, Current)
  }

  /**
   * Simplified weather measurement data, actual weather data event comprises many more data points.
   *
   * @param eventTime unix timestamp when collected
   * @param dataType type of data
   * @param value data point value
   */
  final case class Data(eventTime: Long, dataType: DataType, value: Double)
  final case class TimeWindow(start: Long, end: Long, value: Double)


  def apply(wsid: String): Behavior[Command] = running(wsid, Vector.empty)

  private def average(values: Vector[Double]): Double =
    if (values.isEmpty) Double.NaN
    else values.sum / values.size

  private def running(wsid: String, values: Vector[Data]): Behavior[WeatherStation.Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Record(data, received, replyTo) =>
          val updated = values :+ data
          if (context.log.isDebugEnabled) {
            val averageForSameType = average(updated.filter(_.dataType == data.dataType).map(_.value))
            context.log.debugN("{} total readings from station {}, type {}, average {}, diff: processingTime - eventTime: {} ms",
              updated.size,
              data.dataType,
              wsid,
              averageForSameType,
              received - data.eventTime
            )
          }
          replyTo ! DataRecorded(wsid)
          running(wsid, updated) // store

        case Query(wsid, dataType, func, replyTo) =>
          val valuesForType = values.filter(_.dataType == dataType)
          val value =
            func match {
              case Function.Average =>
                val start: Long = valuesForType.headOption.map(_.eventTime).getOrElse(0)
                val end: Long = valuesForType.lastOption.map(_.eventTime).getOrElse(0)
                Vector(TimeWindow(start, end, average(valuesForType.map(_.value))))

              case Function.HighLow =>
                val (start, min) = valuesForType.map(e => e.eventTime -> e.value).min
                val (end, max) = valuesForType.map(e => e.eventTime -> e.value).max
                Vector(TimeWindow(start, end, min), TimeWindow(start, end, max))

              case Function.Current =>
                Vector(valuesForType.lastOption
                  .map(e => TimeWindow(e.eventTime, e.eventTime, e.value))
                  .getOrElse(TimeWindow(0, 0, 0.0)))

          }

          replyTo ! QueryResult(wsid, dataType, func, valuesForType.size, value)
          Behaviors.same
      }.receiveSignal {
        case (_, PostStop) =>
          context.log.info("Stopping, losing all recorded state for station {}", wsid)
          Behaviors.same
      }
    }
}