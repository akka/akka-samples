import cats.syntax.either._

import scala.util.{Failure, Success, Try}

def parseInt(str: String): Either[String, Int] =
  Either.catchOnly[NumberFormatException](str.toInt).
    leftMap(_ => s"Couldn't read $str")

parseInt("a")
//val n: String = throw new Exception("Who am i")
//parseInt(n)
Either.catchOnly[NumberFormatException]("a".toInt)

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

lazy val timestamp0 = System.currentTimeMillis()

def getTimestamp: Long = {
  val timestamp = System.currentTimeMillis() - timestamp0
  Thread.sleep(100)
  timestamp
}

val timestamps = for {
  a <- Future(getTimestamp)
  b <- Future(getTimestamp)
  c <- Future(getTimestamp)
} yield (a, b, c)

Await.result(timestamps, 1.seconds)

import cats.Cartesian
import cats.instances.option._
import cats.instances.future._
import cats.instances.try_._

Cartesian[Option].product(Some(12), Some(10))
Cartesian[Option].product(None, Some(2))
Cartesian[Option].product(Some(30), None)
Cartesian[Try].product(Success(30), Success(20))
Cartesian[Try].product(Success(30), Failure(new Exception("error")))
Cartesian[Try].product(Failure(new Exception("error")), Success(2))
Await.result(Cartesian[Future].product(Future(getTimestamp), Future(getTimestamp)), 1.second)

Cartesian.map3(Option(1), Option(5), Option(9))(_ + _ + _)

import cats.syntax.cartesian._
(Option(123) |@| Option("abc")).tupled
(Option(123) |@| Option("abc") |@| Option(true)).tupled

val builder3 = Option(123) |@| Option("abc") |@| Option(true)
builder3.tupled

val n: Option[Boolean] = None
(Option(123) |@| Option("abc") |@| n).tupled

case class Cat1(name: String, born: Int, color: String)

(Option("Garfield") |@| Option(1978) |@| Option("Orange and Black")).map(Cat1)

import cats.Monoid
import cats.instances.boolean._
import cats.instances.int._
import cats.instances.list._
import cats.instances.string._
import cats.syntax.cartesian._

case class Cat(
              name: String,
              yearOfBirth: Int,
              favoriteFoods: List[String]
              )

def catToTuple(cat: Cat) = (cat.name, cat.yearOfBirth, cat.favoriteFoods)

implicit val catMonoid = (
  Monoid[String] |@|
  Monoid[Int] |@|
  Monoid[List[String]]
).imap(Cat.apply)(catToTuple)

Monoid.empty[Cat]
Monoid[Cat].empty

val garfield = Cat("Garfield", 1978, List("Lasagne"))
val heathcliff = Cat("Heathcliff", 1988, List("Junk Food"))

import cats.syntax.monoid._
garfield |+| heathcliff

val futureCat = (
  Future("Garfield") |@|
  Future(1978) |@|
  Future(List("Lasagne"))
) map (Cat.apply)

Await.result(futureCat, 1.second)

Cartesian[List].product(List(1, 2), List(3, 4))
(List(1, 2) |@| List(3, 4)).tupled
(List(1, 3, 5) |@| List(2, 4)).tupled
(List(1, 3) |@| List(2, 4, 6)).tupled
(List(1, 3, 5) |@| List(2, 4, 6)).tupled
(List(1, 2) |@| List(3, 4) |@| List(5, 7)).tupled

import cats.instances.either._
type ErrorOr[A] = Either[Vector[String], A]

Cartesian[ErrorOr].product(
  Right(true),
  //Left(Vector("Error 1")),
  Left(Vector("Error 2"))
)
//(Right(2): ErrorOr[Int] |@| (Left(Vector("Error 1")): ErrorOr[Int]) |@| Left(Vector("Error 2"))).tupled

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._

def product[M[_]: Monad, A, B](fa: M[A], fb: M[B]): M[(A, B)] = {
  fa.flatMap( a => fb.map(b => (a, b)))
}

type ErrorOr2[A] = Either[Vector[String], A]
product[ErrorOr2, Int, Int](
  Left(Vector("Error 1")),
  Left(Vector("Error 2"))
)

val f1 = Future("Future 1")
val f2 = Future("Future 2")
for {
  x <- f1
  y <- f2
} yield (x, y)