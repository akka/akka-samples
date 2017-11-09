package monads.movieticket

import com.typesafe.config.ConfigFactory

/**
  * Created by liaoshifu on 17/11/1
  *
  *
  */
package object model {

  // Imposing artificial limit for the seating in the system
  val MAX_SEATING = ConfigFactory.load("application_mt").getConfig("application").getInt("max-seating")
  val IMDBID_REGEX = """imdb\d{4}"""
  val SCREENID_REGEX = """screen_\d{4}"""

  type IMDBID = String
  type ScreenID = String
  type UserId = String
}
