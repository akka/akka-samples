package monads.movieticket.actors

import akka.actor.Actor
import monads.movieticket.domain.GetIMDB
import monads.movieticket.model.MovieDetail
import monads.movieticket.domain.Service._

/**
  * Created by liaoshifu on 17/11/2
  */
class IMDBActor extends Actor {

  override def receive: Receive = {
    case GetIMDB(imdbId) =>
      sender() ! {
        if (containsImdbId(imdbId)) Some(MovieDetail(imdbId, "The James LeBrons ")) else None
      }
  }
}
