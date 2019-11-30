package sample.killrweather

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

/** The entry point to the cluster and sharded data aggregates. */
private[killrweather] object Guardian {

  type WeatherStationId = String

  sealed trait Command extends CborSerializable
  /** Data received by the cluster from stations, read from their devices.
   * `wsid` maps to the Cluster Sharding `EntityTypeKey`.
   */
  final case class Ingest(wsid: WeatherStationId, data: Aggregator.Data, replyTo: ActorRef[WeatherRoutes.DataIngested]) extends Command
  final case class AddWeatherStation(ws: WeatherStationId, replyTo: ActorRef[WeatherRoutes.WeatherStationAdded]) extends Command
  final case class RemoveWeatherStation(wsid: WeatherStationId) extends Command
  final case class GetWeatherStations(replyTo: ActorRef[WeatherRoutes.WeatherStations]) extends Command
  final case class AggregateCommand(wsid: WeatherStationId,
                                    dataType: Aggregator.DataType,
                                    func: Aggregator.Function,
                                    replyTo: ActorRef[WeatherRoutes.QueryResult]) extends Command
  final case class WeatherStation(id: WeatherStationId)

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      WeatherAggregator.init(context.system)
      new Guardian(context.system).active(Set.empty)
    }
  }
}

private[killrweather] final class Guardian(system: ActorSystem[Nothing]) {

  private val sharding = ClusterSharding(system)

  def active(stations: Set[Guardian.WeatherStationId]): Behavior[Guardian.Command] = {
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Guardian.Ingest(wsid, data, replyTo) =>
          val processTimestamp = System.currentTimeMillis
          // default 100 shards, 50 geo-locations
          sharding.entityRefFor(WeatherAggregator.TypeKey, wsid) ! Aggregator.Aggregate(data, processTimestamp, replyTo)
          Behaviors.same

        case Guardian.AggregateCommand(wsid, dataType, func, replyTo) =>
          sharding.entityRefFor(WeatherAggregator.TypeKey, wsid)  ! Aggregator.Query(wsid, dataType, func, replyTo)
          Behaviors.same

        case Guardian.GetWeatherStations(replyTo) =>
          replyTo ! WeatherRoutes.WeatherStations(stations)
          Behaviors.same

        case Guardian.AddWeatherStation(wsid, replyTo) =>
          active(stations = stations + wsid)
          replyTo ! WeatherRoutes.WeatherStationAdded(wsid)
          Behaviors.same

        case Guardian.RemoveWeatherStation(wsid) =>
          active(stations = stations - wsid)
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }
  }
}
