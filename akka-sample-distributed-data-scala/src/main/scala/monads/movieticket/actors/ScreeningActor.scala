package monads.movieticket.actors

import akka.actor.Actor
import monads.movieticket.domain.Contains
import monads.movieticket.domain.Service._

/**
  * Created by liaoshifu on 17/11/2
  */
class ScreeningActor extends Actor {

  override def receive: Receive = {
    case Contains(screenId) =>
      sender() ! { containsScreenId(screenId) }
  }
}
