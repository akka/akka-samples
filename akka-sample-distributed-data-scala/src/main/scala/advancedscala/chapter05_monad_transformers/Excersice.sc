import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import cats.data.EitherT
import cats.instances.either._
import cats.syntax.either._
import cats.instances.future._
import cats.syntax.applicative._

//type Response[A] = Future[Either[String, A]]

type EitherOr[A] = Either[String, A]
type FutureEitherOr[A] = EitherT[Future, String, A]
type Response[A] = FutureEitherOr[A]

val powerLevels = Map(
  "Jazz" -> 6,
  "Bumblebee" -> 8,
  "Hot Rod" -> 10
)

def getPowerLevel(autobot: String): Response[Int] = {
  powerLevels.get(autobot) match {
    case Some(l) => EitherT.right(Future(l))// l.pure[Response]
    case None => EitherT.left(Future(s"$autobot unreachable"))
      //val l: Either[String, Int] = Left(s"$autobot is not found")
      //EitherT(Future.successful(l))
  }
}

getPowerLevel("Jazz")
getPowerLevel("James")

def canSpecialMove(
                  ally1: String,
                  ally2: String
                  ): Response[Boolean] = {
  for {
    l1 <- getPowerLevel(ally1)
    l2 <- getPowerLevel(ally2)
  } yield (l1 + l2) > 15
}
import scala.concurrent.duration._
Await.result(canSpecialMove("Jazz", "Hot Rod").value, 10.seconds)
Await.result(canSpecialMove("Jazz", "Bumblebee").value, 10.seconds)
Await.result(canSpecialMove("James", "Wade").value, 10.seconds)

def tacticalReport(
                  ally1: String,
                  ally2: String
                  ): String = {
  val r = Await.result(canSpecialMove(ally1, ally2).value, 10.seconds)
  r match {
    case Right(false) => s"$ally1 and $ally2 need a recharge"
    case Right(true) => s"$ally1 and $ally2 are ready to roll out!"
    case Left(m) => s"Comms error: $m"
  }
}

tacticalReport("Jazz", "Bumblebee")
tacticalReport("Bumblebee", "Hot Rod")
tacticalReport("Jazz", "Ironhide")
