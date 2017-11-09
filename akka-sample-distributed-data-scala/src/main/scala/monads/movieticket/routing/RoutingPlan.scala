package monads.movieticket.routing

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.pattern.CircuitBreaker
import monads.movieticket.model.{MovieRegistration, ReservationRequest, ReservationStatus}
import monads.movieticket.service.ReservationService
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * Created by liaoshifu on 17/11/1
  * Route builder with Json marshalling/unmarshalling support
  */
final class RoutingPlan(
                 service: ReservationService,
                 breaker: CircuitBreaker
                 ) extends SprayJsonSupport with DefaultJsonProtocol {

  private implicit val reservationRequestFormat = jsonFormat2(ReservationRequest)

  private implicit val reservationResponseFormat = jsonFormat5(ReservationStatus)

  private implicit val movieRequestFormat = jsonFormat3(MovieRegistration)

  /**
    * Request URL: PUT http://<host>:<port>/movie
    * with following 'application/json' as the body
    * {
    *   "imdbId": "imdbId0001",
    *   "availableSeats": 100,
    *   "screenId": "screen_1234
    * }
    *
    * Response contains HTTP Status only
    *
    * 200 if new movie registered or the details of the movie updated,
    * 304 if nothing changed, could be caused by missing external information or other error.
    *
    */

  private def putMovie(implicit ec: ExecutionContext): Route = put {
    entity(as[MovieRegistration]) { movie =>
      println(s"<------- The movie registration is $movie ------->")
      val createMovieRegistration = service.saveOrUpdate(movie)
      println(s"<----- the created movie registration is $createMovieRegistration --------->")
      onCompleteWithBreaker(breaker)(createMovieRegistration) {
        case Success(value) => value match {
          case true => complete(StatusCodes.OK)
          case false => complete(StatusCodes.NotModified)
        }

        case Failure(ex) =>
          extractLog { log =>
            log.debug(s"An error occurred: ${ex.getMessage}")
            complete(StatusCodes.InternalServerError)
          }
      }
    }
  }

  /**
    * Request URL: GET http://<host>:<port>/movie?imdbId=imdbid0001&screendId=screenId1234
    * with no HTTP body attached.
    *
    * Response contains HTTP Status only
    *
    * 200 if movie information is registered or seating information is update.
    * 404 if no movie information is registered previously in the system
    *
    */
  private def getMovie(implicit ec: ExecutionContext): Route = get {
    parameters('imdbId.as[String], 'screenId.as[String]) { (imdbId, screenId) =>
      val request = ReservationRequest(imdbId, screenId)
      println(s"<--------- The request is : $request ------------->")
      val mybeItem = service.fetch(request)

      onCompleteWithBreaker(breaker)(mybeItem) {
        case Success(value) =>
          value match {
            case Some(item) =>
              complete(StatusCodes.OK, item)

            case None =>
              complete(StatusCodes.NotFound)
          }

        case Failure(ex) =>
          extractLog { log =>
            log.debug(s"An error occurred: ${ex.getMessage}")
            complete(StatusCodes.InternalServerError)
          }
      }
    }
  }

  /**
    * Compose the put and get routes defined above to work with the "http://<host>:<port>/movie path
    */
  def movie(implicit ec: ExecutionContext): Route = path("movie") {
    putMovie ~ getMovie
  }

  /**
    * Request URL: POST http://<host>:<port>/reservation
    * with following 'application/json' as the body
    * {
    *   "imdbId": "imdbId0001",
    *   "screenId": "screen_1234"
    * }
    *
    * Response contains HTTP Status only
    *
    * 200 if movie information is registered or seating information is updated.
    * 404 if no movie information is registered previously in the system
    **/
  def reservation(implicit ec: ExecutionContext): Route = path("reservation") {
    post {
      entity(as[ReservationRequest]) { requestOrder =>
        val saved = service.add(requestOrder)

        onCompleteWithBreaker(breaker)(saved) {
          case Success(value) =>
            value match {
              case true => complete(StatusCodes.OK)
              case false => complete(StatusCodes.NotFound)
            }

          case Failure(ex) =>
            extractLog { log =>
              log.debug(s"An error occurred: ${ex.getMessage}")
              complete(StatusCodes.InternalServerError)
            }
        }
      }
    }
  }
}
