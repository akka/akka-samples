package monads.movieticket

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import cats.~>
import monads.movieticket.domain._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by liaoshifu on 17/11/1
  */
package object interpreter {

  /**
    * Step 5: Define the interpreter
    */
  implicit val timeout = Timeout(5 seconds)

  import scala.concurrent.ExecutionContext.Implicits.global

  def actorInterpreter(imdb: ActorRef, screening: ActorRef, reservation: ActorRef): Service ~> Future = new (Service ~> Future) {
    override def apply[A](fa: Service[A]): Future[A] = fa match {
      case getDetail: GetIMDB => (imdb ? getDetail).map(_.asInstanceOf[A])

      case contains: Contains => (screening ? contains).map(_.asInstanceOf[A])

      case get: GetReservation => (reservation ? get).map(_.asInstanceOf[A])

      case put: PutReservation => (reservation ? put).map(_.asInstanceOf[A])

      case reserve: ReserveSeat => (reservation ? reserve).map(_.asInstanceOf[A])
    }
  }
}
