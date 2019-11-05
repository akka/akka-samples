package sample.killrweather

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

/** The entry point to the cluster and sharded data aggregates. */
private[killrweather] object Guardian {

  type WeatherStationId = String

  sealed trait Command extends CborSerializable
  final case class AddWeatherStation(ws: WeatherStation) extends Command
  final case class RemoveWeatherStation(wsid: WeatherStationId) extends Command
  final case class GetWeatherStations(replyTo: ActorRef[Command]) extends Command
  final case class AggregateCommand(wsid: WeatherStationId, dataType: Aggregator.DataType, func: Aggregator.Function) extends Command

  /** Sent from device aggregators to act on. */
  trait AggregateData extends Command {
    def wsid: WeatherStationId
    def readings: Int
  }

  final case class HighLow(wsid: WeatherStationId, dataType: Aggregator.DataType, high: Double, low: Double, readings: Int)
      extends AggregateData

  final case class Average(wsid: WeatherStationId, dataType: Aggregator.DataType, average: Double, readings: Int)
      extends AggregateData

  /** Data received by the cluster from stations, read from their devices.
   * `wsid` maps to the Cluster Sharding `EntityTypeKey`.
   */
  final case class Ingest(wsid: WeatherStationId, data: Aggregator.Data) extends Command
  final case class WeatherStations(stations: Set[WeatherStationId]) extends Command
  final case class WeatherStation(id: String,
                                  name: String = "",
                                  countryCode: String = "",
                                  callSign: String = "",
                                  lat: Double = 0.0,
                                  long: Double = 0.0,
                                  elevation: Double = 0.0)

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      WeatherAggregator.init(context.system)
      new Guardian(context.system).active(Set.empty)
    }
  }
}

private[killrweather] final class Guardian(system: ActorSystem[Nothing]) {

  private val sharding = ClusterSharding(system)

  private def sharded(entityId: String) = {
    sharding.entityRefFor(WeatherAggregator.TypeKey, entityId)
  }

  def active(stations: Set[Guardian.WeatherStationId]): Behavior[Guardian.Command] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Guardian.Ingest(wsid, data) =>
          // TODO after add station, check stations.contains(wsid)
          // default 100 shards, 50 geo-locations
          val entityRef = sharded(wsid)
          entityRef ! Aggregator.Aggregate(data, System.currentTimeMillis)
          Behaviors.same

        case Guardian.Average(wsid, dataType, average, readings) =>
          if (readings > 0)
            context.log.info(s"wsid=$wsid, average=$average for $dataType readings=$readings")

          Behaviors.same

        case Guardian.AggregateCommand(wsid, dataType, func) =>
          if (stations.contains(wsid)) {
            sharded(wsid) ! Aggregator.Get(wsid, dataType, func, context.self)
          }
          Behaviors.same

        case Guardian.GetWeatherStations(replyTo) =>
          replyTo ! Guardian.WeatherStations(stations)
          Behaviors.same

        case Guardian.AddWeatherStation(ws) =>
          active(stations = stations + ws.id)
          Behaviors.same

        case Guardian.RemoveWeatherStation(wsid) =>
          active(stations = stations - wsid)
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }
  }
}
