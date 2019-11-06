package sample.killrweather.fog

private[fog] object JsonFormats {
  // This formatter determines how to convert to and from Data objects:
  import spray.json.RootJsonFormat
  // import the default encoders for primitive types (Int, String, Lists etc)
  import spray.json.DefaultJsonProtocol._

  implicit val weatherStationFormat: RootJsonFormat[WeatherStation.WmoId] = jsonFormat7(WeatherStation.WmoId)
  implicit val dataFormat: RootJsonFormat[WeatherApi.Data] = jsonFormat3(WeatherApi.Data)
}
