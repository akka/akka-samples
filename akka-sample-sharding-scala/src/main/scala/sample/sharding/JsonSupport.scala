package sample.sharding

import spray.json.DefaultJsonProtocol

object JsonFormats {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val dataFormat = jsonFormat3(Guardian.UpdateDevice)
  implicit val stationsFormat = jsonFormat1(Guardian.WeatherStations)

}
