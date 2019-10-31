package sample.sharding

import akka.actor.typed.ActorRef

object Protocol {

  trait Command extends CborSerializable
  type WeatherStationId = Int

  final case class GetWeatherStations(replyTo: ActorRef[WeatherStations]) extends Command
  final case class AddWeatherStation(sid: WeatherStationId) extends Command
  final case class GetAggregate(stationId: WeatherStationId, deviceId: Int, replyTo: ActorRef[AggregateData])
      extends Command
  final case class GetHiLow(stationId: WeatherStationId, deviceId: Int, replyTo: ActorRef[AggregateData])
      extends Command

  /** Data collected over a window of time. */
  final case class UpdateDevice(stationId: WeatherStationId, deviceId: Int, data: List[Double]) extends Command

  /** Data received by the cluster from stations, read from their devices.
   *  Each sampling would normally have a timestamp.
   */
  final case class Data(stationId: WeatherStationId, deviceId: Int, latest: Double) extends Command

  // TODO not commands, fix in registry
  sealed trait Response extends Command
  final case class WeatherStations(stations: Seq[WeatherStation]) extends Response
  object WeatherStations {
    def apply(stations: Set[WeatherStationId]): WeatherStations =
      WeatherStations(stations.toSeq.map(id => WeatherStation(id, Set.empty)))
  }
  final case class WeatherStation(id: WeatherStationId, devices: Set[Data])

  trait AggregateData extends Response {
    def stationId: Int
    def deviceId: Int
    def readings: Int
  }

  final case class HighLow(stationId: WeatherStationId, deviceId: Int, high: Double, low: Double, readings: Int)
      extends AggregateData

  final case class Aggregate(stationId: WeatherStationId, deviceId: Int, average: Double, latest: Double, readings: Int)
      extends AggregateData
  object Aggregate {

    def apply(stationId: WeatherStationId, deviceId: Int, values: Vector[Double]): Aggregate =
      if (values.isEmpty)
        Aggregate(stationId, deviceId, Double.NaN, Double.NaN, 0)
      else
        Aggregate(stationId, deviceId, average(values), values.last, values.size)

    def average(values: Vector[Double]): Double =
      if (values.isEmpty) Double.NaN
      else values.sum / values.size
  }

}
