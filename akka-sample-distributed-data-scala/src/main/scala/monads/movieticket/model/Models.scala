package monads.movieticket.model

/**
  * Created by liaoshifu on 17/11/1
  * Defines all data model used in the system
  * Included boundary check or formation validation as part of the last line of data sanity check
  */

final case class MovieDetail(imdbId: IMDBID, movieTitle: String)

final case class MovieRegistration(
                                  imdbId: IMDBID,
                                  availableSeats: Int = MAX_SEATING,
                                  screenID: ScreenID
                                  ) {
  require(imdbId.matches(IMDBID_REGEX), "Invalid IMDB Id")
  require(screenID.matches(SCREENID_REGEX), "Invalid screen Id")
  require(availableSeats >= 0 && availableSeats <= MAX_SEATING, "Available seats exceeded maximum capacity")
}

final case class ReservationRequest(imdbId: IMDBID,
                                    screenId: ScreenID) {
  require(imdbId.matches(IMDBID_REGEX), "Invalid IMDB Id")
  require(screenId.matches(SCREENID_REGEX), "Invalid Screen Id")
}

final case class ReservationStatus(imdbId: IMDBID,
                                   screenId: ScreenID,
                                   movieTitle: String,
                                   availableSeats: Int = MAX_SEATING,
                                   reservedSeats: Int = 0) {
  require(imdbId.matches(IMDBID_REGEX), "Invalid IMDB Id")
  require(screenId.matches(SCREENID_REGEX), "Invalid Screen Id")
  require(availableSeats >= 0 && availableSeats <= MAX_SEATING , "Available seats exceeded maximum capacity.")
  require(reservedSeats >= 0 && reservedSeats <= availableSeats, "Reserved Seats can not be more than available seats")
}