package monads.movieticket.actors

import akka.actor.Actor
import monads.movieticket.domain.{GetReservation, PutReservation, ReserveSeat}
import monads.movieticket.domain.Service.{ImdbIds, Screens}
import monads.movieticket.model.{ReservationRequest, ReservationStatus}

/**
  * Created by liaoshifu on 17/11/2
  */
class ReservationActor extends Actor {

  private var demoStorage: Map[ReservationRequest, ReservationStatus] = Map.empty

  override def receive: Receive = {
    case GetReservation(request) =>
      sender() ! demoStorage.get(request)

    case PutReservation(request, status) =>
      sender() ! { demoStorage.get(request) match {
        case Some(oldStatus) =>
          val updatedStatus = oldStatus.copy(movieTitle = status.movieTitle, availableSeats = status.availableSeats)
          demoStorage = demoStorage.updated(request, updatedStatus)
          true

        case None =>
          if (ImdbIds.contains(request.imdbId) && Screens.contains(request.screenId)) {
            demoStorage = demoStorage + (request -> status)
            true
          } else false
      }
      }

    case ReserveSeat(request) =>
      sender() ! {
        demoStorage.get(request) match {
          case Some(status) =>
            val newStatus = if (status.reservedSeats < status.availableSeats) {
              status.copy(reservedSeats = status.reservedSeats + 1, availableSeats = status.availableSeats - 1)
            } else {
              status
            }

            demoStorage = demoStorage + (request -> newStatus)
            true

          case None =>
            false
        }
      }
  }
}
