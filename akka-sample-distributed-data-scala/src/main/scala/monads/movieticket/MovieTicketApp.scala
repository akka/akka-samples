package monads.movieticket

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.pattern.CircuitBreaker
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import monads.movieticket.actors.{IMDBActor, ReservationActor, ScreeningActor}
import monads.movieticket.routing.RoutingPlan
import monads.movieticket.service.ReservationService

import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Failure

/**
  * Created by liaoshifu on 17/11/1
  */
object MovieTicketApp extends App {

  val config = ConfigFactory.load("application_mt")
  implicit val system = ActorSystem("movie-ticket", config)
  implicit val ec = system.dispatcher

  implicit val log = Logging(system, getClass)
  implicit val materializer = ActorMaterializer()

  val httpConfig = config.getConfig("http")

  /**
    * adding CircuitBreaker to regulate external work load
    */
  val breaker = new CircuitBreaker(system.scheduler,
    maxFailures = 5,
    callTimeout = 5.seconds,
    resetTimeout = 1.second
  )

  val routings = new RoutingPlan(
    service = new ReservationService(
      imdbSource = system.actorOf(Props[IMDBActor], "imdb"),
      screenInfo = system.actorOf(Props[ScreeningActor], "screening"),
      reservationSource = system.actorOf(Props[ReservationActor], "reservation")
    ),
    breaker
  )

  val handler: Route = routings.movie ~ routings.reservation

  val (httpHost, httpPort) = (httpConfig.getString("host"), httpConfig.getInt("port"))

  val bindingFuture = Http().bindAndHandle(handler, httpHost, httpPort)

  StdIn.readLine()

  bindingFuture
    .flatMap(_.unbind())
    .onComplete {
      case Failure(ex) =>
        log.error(ex, "Failed to bind to {}:{}!", httpHost, httpPort)
        system.terminate()

      case _ =>
        system.terminate()
    }
}
