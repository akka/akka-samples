import cats.Applicative
import cats.syntax.cartesian._
import cats.instances.future._
import cats.syntax.applicative._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

def getUptime(host: String): Future[Int] =
  Future(host.length * 60)

def newCombine(
              accum: Future[List[Int]],
              host: String
              ): Future[List[Int]] = {
  (accum |@| getUptime(host)).map(_ :+ _)
}

val hostnames = List(
  "spark.apache.org",
  "www.playframework.com",
  "akka.io",
  "www.scala-lang.org"
)

import scala.language.higherKinds

def listTraverse[F[_]: Applicative, A, B](
                                         list: List[A]
                                         )(func: A => F[B]): F[List[B]] =
  list.foldLeft(List.empty[B].pure[F]) { (fb, a) =>
    (fb |@| func(a)).map(_ :+ _)
  }

def listSequence[F[_]: Applicative, B](list: List[F[B]]): F[List[B]] =
  listTraverse(list)(identity)

Await.result(
  listSequence(hostnames.map(getUptime)),
  1 second
)

import cats.instances.vector._
listSequence(List(Vector(1, 2)))
listSequence(List(Vector(1, 2), Vector(3, 5, 7)))
listSequence((List(Vector(1, 3, 7), Vector(2, 9), Vector(8, 10, 6, 5))))

import cats.instances.option._

def process(inputs: List[Int]) =
  listTraverse(inputs)(n => if (n % 2 == 0) Some(n) else None)

process(List(1, 3, 6))
process(List(2, 4, 6))

import cats.data.Validated
import cats.instances.list._

type ErrorsOr[A] = Validated[List[String], A]

def process2(inputs: List[Int]): ErrorsOr[List[Int]] =
  listTraverse(inputs) { n =>
    if (n % 2 == 0)
      Validated.valid(n)
    else
      Validated.invalid(List(s"$n is not even"))
  }

process2(List(2, 4, 6))
process2(List(1, 3, 5, 6, 8, 9))

import cats.Traverse
Await.result(
  Traverse[List].traverse(hostnames)(getUptime),
  1 second
)

val numbers = List(Future(3), Future(5), Future(9))
Await.result(
  Traverse[List].sequence(numbers),
  2 seconds
)

import cats.syntax.traverse._
Await.result(hostnames.traverse(getUptime), 1 second)
Await.result(numbers.sequence, 1 second)

val eithers: List[Either[String, String]] = List(
  Right("Wow!"),
  Right("Such cool!")
)

eithers.sequence
