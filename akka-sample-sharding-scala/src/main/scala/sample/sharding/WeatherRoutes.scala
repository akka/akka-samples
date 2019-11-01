package sample.sharding

import java.util.concurrent.TimeUnit

import scala.util.{ Failure, Success }
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.{ actor => classic }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.util.Timeout

class WeatherRoutes(guardian: ActorRef[Guardian.Command])(implicit system: ActorSystem[_]) {

  import akka.actor.typed.scaladsl.adapter._
  implicit val classicSystem: classic.ActorSystem = system.toClassic

  implicit val timeout: Timeout =
    Timeout(system.settings.config.getDuration("weather.routes.ask-timeout").toMillis, TimeUnit.MILLISECONDS)

  import akka.actor.typed.scaladsl.AskPattern._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import akka.http.scaladsl.server.Directives._
  import JsonFormats._
  import Guardian._

  val weather: Route =
    concat(
      pathPrefix("temperatures") {
        post {
          entity(as[UpdateDevice]) { data =>
            guardian ! data
            complete(s"KillrWeather updating data for temperature device ${data.deviceId}")
          }
        }
      },
      pathPrefix("weather-stations") {
        get {
          onComplete((guardian ? GetWeatherStations).mapTo[WeatherStations]) {
            case Success(value) => complete(s"The result was $value")
            case Failure(e)     => complete((StatusCodes.InternalServerError, s"An error occurred: ${e.getMessage}"))
          }
        }
      })
}
