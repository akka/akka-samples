package monads.movieticket

import cats.free._
import cats.free.Free._
import monads.movieticket.model._

/**
  * Created by liaoshifu on 17/11/1
  *
  * Free Monad Six steps
  */

package object domain {


  /**
    * Step 1: Create ADT language
    * @tparam A
    */
  sealed trait Service[A]

  final case class GetIMDB(imdbId: IMDBID) extends Service[Option[MovieDetail]]
  final case class Contains(screenId: ScreenID) extends Service[Boolean]
  final case class GetReservation(request: ReservationRequest) extends Service[Option[ReservationStatus]]
  final case class PutReservation(request: ReservationRequest, status: ReservationStatus) extends Service[Boolean]
  final case class ReserveSeat(request: ReservationRequest) extends Service[Boolean]

  /**
    * Step 2: Create Free monad type
    * @tparam A
    */
  type ServiceF[A] = Free[Service, A]

  /**
    * Step 3: Smart constructor lift to monad
    */
  object Service {
    def get(imdbId: IMDBID): ServiceF[Option[MovieDetail]] = liftF(GetIMDB(imdbId))

    def contains(screenID: ScreenID): ServiceF[Boolean] = liftF(Contains(screenID))

    def put(imdbId: IMDBID, screenID: ScreenID, status: ReservationStatus): ServiceF[Boolean] =
      liftF(PutReservation(ReservationRequest(imdbId, screenID), status))

    def reserveSeat(request: ReservationRequest): ServiceF[Boolean] = liftF(ReserveSeat(request))

    def getStatus(request: ReservationRequest): ServiceF[Option[ReservationStatus]] = liftF(GetReservation(request))

    //val DEFAULT_IMDBID = "imdb0001"

    //val DEFAULT_SCREENID = "screen_1234"

    val ImdbIds = Set("imdb0001", "imdb0002", "imdb0003", "imdb0004", "imdb0005")
    val Screens = Set("screen_1234", "screen_2345", "screen_3456")

    def containsImdbId(imdbId: IMDBID): Boolean = ImdbIds.contains(imdbId)

    def containsScreenId(screenId: ScreenID): Boolean = Screens.contains(screenId)
  }

  import Service._

  /**
    * Step 4: Program action
    */

  def saveOrUpdate(registration: MovieRegistration): ServiceF[Boolean] = {
    for {
      movieOption <- get(registration.imdbId)
      screenScheduled <- contains(registration.screenID)
      /*registered <- put(
        registration.imdbId,
        registration.screenID,
        if (movieOption.isDefined && screenScheduled) {
          ReservationStatus(
            registration.imdbId,
            registration.screenID,
            movieOption.get.movieTitle,
            registration.availableSeats
          )
        } else {
          ReservationStatus(
            registration.imdbId,
            registration.screenID,
            "no movie title"
          )
        }
      )*/
      registered <- if (movieOption.isDefined && screenScheduled) {
        put(
          registration.imdbId,
          registration.screenID,
          ReservationStatus(
            registration.imdbId,
            registration.screenID,
            movieOption.get.movieTitle,
            registration.availableSeats
          )
        )
      } else {
        Free.pure[Service, Boolean](false)
      }
    } yield registered
  }

  def reserve(request: ReservationRequest): ServiceF[Boolean] = reserveSeat(request)

  def fetch(request: ReservationRequest): ServiceF[Option[ReservationStatus]] = getStatus(request)

}
