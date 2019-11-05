package sample.killrweather

import scala.concurrent.duration._

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.{actor => classic}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.util.{Failure, Success}

/** HTTP API for
 * 1. Receiving data from remote weather stations
 *    A. Adding a new station when it comes online TODO: and marking if downed
 *    B. Device samplings over windowed time slices
 * 2. Receiving and responding to queries
 *
 * @param guardian the entry point to the cluster and sharded data aggregates
 */
private[killrweather] final class WeatherRoutes(guardian: ActorRef[Guardian.Command])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.AskPattern._
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
            guardian ! Guardian.Ingest(data.wsid, data)
            complete(s"KillrWeather received latest ${data.wsid} data.")
          }
        },
        get {
          onComplete((guardian ? Guardian.GetWeatherStations).mapTo[Guardian.WeatherStations]) {
            case Success(value) => complete(s"The result was $value")
            case Failure(e)     => complete(withFailure(e, classOf[Guardian.GetWeatherStations]))
          }
        },
        (path(Segment) & (put | post)) { wsid =>
          entity(as[Guardian.WeatherStation]) { ws =>
            guardian ! Guardian.AddWeatherStation(ws)
            complete(StatusCodes.Accepted)
          }
        })
    }

  private def withFailure(e: Throwable, failureContext: Class[_]): (StatusCodes.ServerError, String) =
    (StatusCodes.InternalServerError, s"An error occurred completing $failureContext: ${e.getMessage}")
}
