package sample.killrweather

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

/** The entry point to the cluster and sharded data aggregates. */
private[killrweather] object Stations {

  type WeatherStationId = String

  sealed trait Command extends CborSerializable

  final case class AddWeatherStation(wsid: WeatherStationId, replyTo: ActorRef[WeatherStationAdded]) extends Command
  final case class WeatherStationAdded(wsid: WeatherStationId) extends CborSerializable

  final case class RemoveWeatherStation(wsid: WeatherStationId) extends Command

  final case class GetWeatherStations(replyTo: ActorRef[WeatherStations]) extends Command
  final case class WeatherStations(ids: Set[WeatherStationId]) extends CborSerializable

  final case class Query(wsid: WeatherStationId,
                         query: WeatherStation.Query) extends Command


  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      val sharding = ClusterSharding(context.system)
      new Stations(context, sharding).active(Set.empty)
    }
  }
}

private[killrweather] final class Stations(context: ActorContext[Stations.Command], sharding: ClusterSharding) {

  import Stations._

  // FIXME this list is node local, which is weird in a distributed app, should we just skip this actor perhaps?
  // Make it a singleton?
  def active(stations: Set[Stations.WeatherStationId]): Behavior[Stations.Command] = {
    Behaviors.receiveMessage {
      case GetWeatherStations(replyTo) =>
        replyTo ! WeatherStations(stations)
        Behaviors.same

      case AddWeatherStation(wsid, replyTo) =>
        context.log.info("Adding weather station {}", wsid)
        // We don't actually though, should we have an Init message for it?
        replyTo ! WeatherStationAdded(wsid)
        active(stations = stations + wsid)

      case RemoveWeatherStation(wsid) =>
        context.log.info("Removing weather station {}", wsid)
        active(stations = stations - wsid)
    }
  }
}
