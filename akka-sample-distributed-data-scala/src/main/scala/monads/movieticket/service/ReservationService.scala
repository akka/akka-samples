package monads.movieticket.service

import akka.actor.ActorRef
import cats.~>
import cats.instances.future._
import monads.movieticket.domain.Service
import monads.movieticket.interpreter
import monads.movieticket.model.{MovieRegistration, ReservationRequest, ReservationStatus}
import monads.movieticket.domain

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by liaoshifu on 17/11/1
  *
  * Provide the behavior of seat reservation by composing the data from various data source
  */
class ReservationService(
                        imdbSource: ActorRef,
                        screenInfo: ActorRef,
                        reservationSource: ActorRef
                        ) {

  /**
    * Step 6: execute program using interpreter
    */
  private val actorRuntime: Service ~>  Future = interpreter.actorInterpreter(imdbSource, screenInfo, reservationSource)

  /**
    * Asynchronously retrieve external data for Movie details and screen info,
    * once both are obtained, then insert/update the ReservationStatus in the database
    *
    *  @param registration the MovieRegistration constructed by the routing DSL
    *
    *  @param ec implicit variable required for Future execution
    *            @return Future of Boolean, contains true if update was successful, other false
    */
  def saveOrUpdate(registration: MovieRegistration)(implicit ec: ExecutionContext): Future[Boolean] =
    domain.saveOrUpdate(registration).foldMap(actorRuntime)

  /**
    * Asynchronously increase the count of reservation based on the given request
    *
    * @param request the ReservationRequest constructed by the routing DSL
    * @param ec implicit variable required for Future execution
    * @return Future of Boolean, contains true if increment was successful, otherwise false
    *         
    */
  def add(request: ReservationRequest)(implicit ec: ExecutionContext): Future[Boolean] =
    domain.reserve(request).foldMap(actorRuntime)

  /**
    * Asynchronously obtain current ReservationRequest based on the given request
    * @param request the ReservationRequest constructed by the routing DSL
    * @param ec  implicit variable required for Future execution
    * @return  a Future of current reservation status of given request, return Some(detail) if database
    *          does contain related information, otherwise None
    */
  def fetch(request: ReservationRequest)(implicit ec: ExecutionContext): Future[Option[ReservationStatus]] =
    domain.fetch(request).foldMap(actorRuntime)
}
