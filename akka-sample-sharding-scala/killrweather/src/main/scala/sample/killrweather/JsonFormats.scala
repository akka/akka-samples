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
  implicit val dataIngestedFormat: RootJsonFormat[WeatherRoutes.DataIngested] = jsonFormat1(WeatherRoutes.DataIngested)

  implicit val queryWindowFormat: RootJsonFormat[WeatherRoutes.TimeWindow] = jsonFormat3(WeatherRoutes.TimeWindow)
  implicit val queryStatusFormat: RootJsonFormat[WeatherRoutes.QueryResult] = jsonFormat5(WeatherRoutes.QueryResult)

  implicit val stationAddedFormat: RootJsonFormat[WeatherRoutes.WeatherStationAdded] = jsonFormat1(WeatherRoutes.WeatherStationAdded)
  implicit val stationsFormat: RootJsonFormat[WeatherRoutes.WeatherStations] = jsonFormat1(WeatherRoutes.WeatherStations)
  implicit val stationFormat: RootJsonFormat[Guardian.WeatherStation] = jsonFormat1(Guardian.WeatherStation)

}
