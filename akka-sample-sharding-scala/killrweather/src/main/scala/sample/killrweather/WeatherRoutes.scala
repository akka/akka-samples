package sample.killrweather

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.{actor => classic}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object WeatherRoutes {
  sealed trait Status extends CborSerializable
  final case class DataIngested(wsid: String) extends Status
  final case class WeatherStationAdded(wsid: String) extends Status
  final case class WeatherStations(ids: Set[String]) extends Status
  final case class QueryStatus(wsid: String, dataType: String, readings: Int, values: Vector[(String, Double)]) extends Status
}

/** HTTP API for
 * 1. Receiving data from remote weather stations
 *    A. Adding a new station when it comes online / goes offline
 *    B. Device samplings over windowed time slices
 * 2. Receiving and responding to queries
 *
 * @param guardian the entry point to the cluster and sharded data aggregates
 */
private[killrweather] final class WeatherRoutes(guardian: ActorRef[Guardian.Command])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.adapter._
  implicit val classicSystem: classic.ActorSystem = system.toClassic
  implicit val timeout: Timeout = system.settings.config.getDuration("killrweather.routes.ask-timeout").toMillis.millis

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._

  val weather: Route =
    pathPrefix("weather") {
      concat(
        post {
          entity(as[Aggregator.Data]) { data =>
            val f: Future[WeatherRoutes.DataIngested] = guardian.ask(Guardian.Ingest(data.wsid, data, _))
            onSuccess(f) { performed =>
              complete(StatusCodes.Accepted -> s"$performed from event time: ${data.eventTime}")
            }
          }
        },
        (path(Segment) & (put | post)) { wsid =>
          val f: Future[WeatherRoutes.WeatherStationAdded] = guardian.ask(replyTo => Guardian.AddWeatherStation(wsid, replyTo))
          onSuccess(f) { performed =>
            complete(StatusCodes.Created -> performed.wsid)
          }
        },
        get {
          onSuccess(guardian.ask(Guardian.GetWeatherStations)) { extraction =>
            complete(StatusCodes.Created -> extraction)
          }
        })
      // todo curl queries
    }

}
