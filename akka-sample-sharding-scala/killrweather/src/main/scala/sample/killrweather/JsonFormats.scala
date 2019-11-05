package sample.killrweather

/**
 * This formatter determines how to convert to and from Data objects.
 * It is used by the `WeatherRoutes` when receiving remote edge data.
 */
object JsonFormats {

  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val dataFormat: RootJsonFormat[Aggregator.Data] = jsonFormat3(Aggregator.Data)
  implicit val stationsFormat: RootJsonFormat[Guardian.WeatherStations] = jsonFormat1(Guardian.WeatherStations)
  implicit val stationFormat: RootJsonFormat[Guardian.WeatherStation] = jsonFormat7(Guardian.WeatherStation)

}
