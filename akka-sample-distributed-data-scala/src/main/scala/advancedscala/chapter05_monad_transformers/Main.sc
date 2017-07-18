import cats.Monad
import cats.syntax.applicative._

import scala.util._

case class User(id: Long, name: String)
val db = Map(1L -> User(1, "James"), 2 -> User(2L, "Wade"))

def lookupUser(id: Long): Either[Error, Option[User]] = Right(db.get(id))

def lookupUserName(id: Long): Either[Error, Option[String]] =
  for {
    optUser <- lookupUser(id)
  } yield {
    for {
      user <- optUser
    } yield user.name
  }

// impossible
def compose[M1[_]: Monad, M2[_]: Monad] = {
  type Composed[A] = M1[M2[A]]
  new Monad[Composed] {
    def pure[A](a: A): Composed[A] = a.pure[M2].pure[M1]

    def flatMap[A, B](fa: Composed[A])(f: A => Composed[B]): Composed[B] = ???

    override def tailRecM[A, B](a: A)(f: (A) => Composed[Either[A, B]]) = ???
  }
}

import cats.data.OptionT
type ListOption[A] = OptionT[List, A]

import cats.Monad
import cats.instances.list._
import cats.syntax.applicative._

val result: ListOption[Int] = 42.pure[ListOption]

val a = 10.pure[ListOption]
val b = 32.pure[ListOption]

a.value == List(Option(10))
a flatMap { (x: Int) =>
  b map { (y: Int) =>
    x + y
  }
}

import cats.instances.either._
type Error = String

type ErrorOr[A] = Either[Error, A]

type ErrorOptionOr[A] = OptionT[ErrorOr, A]

val result1 = 41.pure[ErrorOptionOr]
val result2 = result1.flatMap { x => (x + 1).pure[ErrorOptionOr] }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import cats.data.EitherT
import cats.instances.future._

type FutureEither[A] = EitherT[Future, Error, A]

type FutureEitherOrOption[A] = OptionT[FutureEither, A]

val answer: FutureEitherOrOption[Int] = for {
  a <- 10.pure[FutureEitherOrOption]
  b <- 32.pure[FutureEitherOrOption]
} yield a * b

import cats.syntax.either._
import cats.syntax.option._

type ErrorOrOption[A] = OptionT[ErrorOr, A]

// Create using pure
val stack1 = 123.pure[ErrorOrOption]
// Create using apply
val stack2 = OptionT[ErrorOr, Int](
  123.some.asRight
)

val stack3 = OptionT[ErrorOr, Int](None.asRight)

import cats.instances.vector._
import cats.data.Writer

import cats.instances.option._
import cats.syntax.monoid._
import cats.instances.string._
import cats.instances.int._
import cats.instances.try_._

type Logged[A] = Writer[Vector[String], A]
type LoggedFallable[A] = EitherT[Logged, String, A]
type LoggedFallableOption[A] = OptionT[LoggedFallable, A]

val packed = 123.pure[LoggedFallableOption]
val partiallyPacked = packed.value
val completelyUnpacked = partiallyPacked.value

for {
  p <- packed
} yield p

"abc" |+| "cd"
val o1 = Option(1)
val o2 = Option(3)
o1 |+| o2

type OptionLogged[A] = OptionT[Logged, A]

def parseNumber(str: String): Logged[Option[Int]] =
  Try(str.trim.toInt).toOption match {
    case Some(num) => Writer(Vector(s"Read $str"), Some(num))
    case None => Writer(Vector(s"Failed on $str"), None)
  }


val p1 = OptionT(parseNumber("3"))

for {
  p11 <- p1
} yield p11

def addNumbers(
              a: String,
              b: String,
              c: String
              ): Logged[Option[Int]] = {

  val r = for {
    a1 <- OptionT(parseNumber(a))
    b1 <- OptionT(parseNumber(b))
    c1 <- OptionT(parseNumber(c))
  } yield a1 + b1 + c1

  r.value
}

addNumbers("3", "5", "7")
addNumbers("2 ", "bc", "9")

def parseNumberTry(str: String): Logged[Try[Int]] = {
  Writer(Vector(s"Read $str"), Try(str.trim.toInt))
}

/*def addNumbersTry(
                 a: String,
                 b: String,
                 c: String
                 ): Logged[Try[Int]] = {
  val r = for {
    a1 <- parseNumberTry(a)
    b1 <- parseNumberTry(b)
    c1 <- parseNumberTry(c)
  } yield a1 + b1 + c1
}*/


