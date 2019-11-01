package sample.sharding

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }

object Guardian {

  type WeatherStationId = Int

  sealed trait Command extends CborSerializable
  final case class AddWeatherStation(sid: WeatherStationId) extends Command
  final case class GetWeatherStations(replyTo: ActorRef[Command]) extends Command
  final case class GetAverage(stationId: WeatherStationId, deviceId: Int, replyTo: ActorRef[AggregateData])
      extends Command
  final case class GetHiLow(stationId: WeatherStationId, deviceId: Int, replyTo: ActorRef[AggregateData])
      extends Command

  /* Sent from device aggregators to act on. */
  trait AggregateData extends Command {
    def stationId: Int
    def deviceId: Int
    def readings: Int
  }

  final case class HighLow(stationId: WeatherStationId, deviceId: Int, high: Double, low: Double, readings: Int)
      extends AggregateData

  final case class Average(stationId: WeatherStationId, deviceId: Int, average: Double, readings: Int)
      extends AggregateData

  /** Data received by the cluster from stations, read from their devices.
   *  Each sample `data` would normally have a timestamp, here we keep it simple.
   */
  final case class UpdateDevice(stationId: WeatherStationId, deviceId: Int, data: List[Double]) extends Command
  final case class WeatherStations(stations: Set[WeatherStationId]) extends Command

  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(stations: Set[WeatherStationId]): Behavior[Command] =
    Behaviors.setup { context =>
      TemperatureDevice.init(context.system)
      val sharding = ClusterSharding(context.system)

      def sharded(typeKey: EntityTypeKey[Aggregator.Command], id: Int) =
        sharding.entityRefFor(typeKey, id.toString)

      Behaviors.receiveMessage[Command] {
        case UpdateDevice(stationId, deviceId, data) =>
          if (stations.contains(stationId)) {
            sharded(TemperatureDevice.TypeKey, deviceId) ! Aggregator.Aggregate(stationId, deviceId, data)
          }
          Behaviors.same

        case Average(sid, did, average, readings) =>
          if (readings > 0)
            context.log.info(s"Temperature[stationId=$sid, deviceId=$did, average=$average, readings=$readings]")

          Behaviors.same

        case GetAverage(sid, deviceId, replyTo) =>
          if (stations.contains(sid)) {
            sharded(TemperatureDevice.TypeKey, deviceId) ! Aggregator.GetAverage(sid, deviceId, replyTo)
          }
          Behaviors.same

        case GetWeatherStations(replyTo) =>
          replyTo ! WeatherStations(stations)
          Behaviors.same

        case AddWeatherStation(sid) =>
          registry(stations = stations + sid)
          Behaviors.same

        case _ => Behaviors.unhandled
      }
    }

}
