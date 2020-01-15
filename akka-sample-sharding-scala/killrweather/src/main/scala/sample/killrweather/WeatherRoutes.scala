package sample.killrweather

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.Timeout

/** HTTP API for
 * 1. Receiving data from remote weather stations
 *    A. Adding a new station when it comes online / goes offline (to this node, the state is local)
 *    B. Device samplings over windowed time slices
 * 2. Receiving and responding to queries
 *
 * @param stations the entry point to the cluster and sharded data aggregates
 */
private[killrweather] final class WeatherRoutes(stations: ActorRef[Stations.Command], sharding: ClusterSharding)(implicit system: ActorSystem[_]) {

  // timeout used for asking the actor
  private implicit val timeout: Timeout =
    system.settings.config.getDuration("killrweather.routes.ask-timeout").toMillis.millis

  private def addWeatherStation(wsid: String): Future[Stations.WeatherStationAdded] =
    stations.ask(Stations.AddWeatherStation(wsid, _))

  private def recordData(wsid: String, data: WeatherStation.Data): Future[WeatherStation.DataRecorded] = {
    val ref = sharding.entityRefFor(WeatherStation.TypeKey, wsid)
    ref.ask(WeatherStation.Record(data, System.currentTimeMillis, _))
  }

  private def query(wsid: String, dataType: WeatherStation.DataType, function: WeatherStation.Function): Future[WeatherStation.QueryResult] = {
    val ref = sharding.entityRefFor(WeatherStation.TypeKey, wsid)
    ref.ask(WeatherStation.Query(wsid, dataType, function, _))
  }

  // unmarshallers for the query parameters
  private val funcsFromName = WeatherStation.Function.All.map(function => function.toString.toLowerCase -> function).toMap
  private implicit val functionTypeUnmarshaller = Unmarshaller.strict[String, WeatherStation.Function](text => funcsFromName(text.toLowerCase))

  private val dataTypesFromNames = WeatherStation.DataType.All.map(dataType => dataType.toString.toLowerCase -> dataType).toMap
  private implicit val dataTypeUnmarshaller = Unmarshaller.strict[String, WeatherStation.DataType](text => dataTypesFromNames(text.toLowerCase))


  // imports needed for the routes and entity json marshalling
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._

  val weather: Route =
    pathPrefix("weather") {
      concat(
        pathPrefix(Segment) { wsid =>
          concat(
            pathEnd {
              concat(
                get {
                  parameters(("type".as[WeatherStation.DataType], "function".as[WeatherStation.Function])) { (dataType, function) =>
                    complete(query(wsid, dataType, function))
                  }
                },
                post {
                  onSuccess(addWeatherStation(wsid)) { performed =>
                    complete(StatusCodes.Created -> performed.wsid)
                  }
                }
              )
            },
            path("data") {
              post {
                entity(as[WeatherStation.Data]) { data =>
                  onSuccess(recordData(wsid, data)) { performed =>
                    complete(StatusCodes.Accepted -> s"$performed from event time: ${data.eventTime}")
                  }
                }
              }
            }
          )
        },
        pathEnd {
          concat(
            get {
              complete(stations.ask(Stations.GetWeatherStations))
            }
          )
        },
       )
    }

}
