import cats.data.Kleisli
import cats.instances.list._

val step1: Kleisli[List, Int, Int] =
  Kleisli(x => List(x + 1, x - 1))
val step2: Kleisli[List, Int, Int] =
  Kleisli(x => List(x, -x))
val step3: Kleisli[List, Int, Int] =
  Kleisli(x => List(x * 2, x / 2))

val pipeline = step1 andThen step2 andThen step3

pipeline.run(20)

import cats.Semigroup
import cats.data.Validated
import cats.data.Validated._
import cats.syntax.cartesian._
import cats.instances.either._
import cats.instances.list._
import cats.syntax.validated._

sealed trait Predicate[E, A] {
  import Predicate._

  def run(implicit s: Semigroup[E]): A => Either[E, A] =
    (a: A) => this(a).toEither

  def and(that: Predicate[E, A]): Predicate[E, A] = AndPre(this, that)

  def or(that: Predicate[E, A]): Predicate[E, A] = OrPre(this, that)

  def apply(a: A)(implicit s: Semigroup[E]): Validated[E, A] = this match {
    case PurePre(func) => func(a)

    case AndPre(self, other) =>
      (self(a) |@| other(a)).map((_, _) => a)

    case OrPre(self, other) =>
      self(a) match {
        case Valid(v) => Valid(v)
        case Invalid(v1) =>
          other(a) match {
            case Valid(v2) => Valid(v2)
            case Invalid(v2) =>
              Invalid(s.combine(v1, v2))
          }
      }
  }
}

object Predicate {
  final case class PurePre[E, A](func: A => Validated[E, A]) extends Predicate[E, A]
  final case class AndPre[E, A](self: Predicate[E, A], other: Predicate[E, A]) extends Predicate[E, A]
  final case class OrPre[E, A](self: Predicate[E, A], other: Predicate[E, A]) extends Predicate[E, A]

  def apply[E, A](f: A => Validated[E, A]): Predicate[E, A] = PurePre(f)

  def lift[E, A](error: E, func: A => Boolean): Predicate[E, A] =
    PurePre(a => if (func(a)) a.valid else error.invalid)
}

import cats.data.NonEmptyList
type Errors = NonEmptyList[String]
type Result[A] = Either[Errors, A]
type Check[A, B] = Kleisli[Result, A, B]

def check[A, B](func: A => Result[B]): Check[A, B] =
  Kleisli(func)

def checkPred[A](pred: Predicate[Errors, A]): Check[A, A] =
  Kleisli[Result, A, A](pred.run)

def error(s: String): NonEmptyList[String] = NonEmptyList(s, Nil)

def longThan(n: Int): Predicate[Errors, String] = Predicate.lift(
  error(s"Must be longer that $n characters"),
  str => str.size > n
)

val alphanumeric: Predicate[Errors, String] = Predicate.lift(
  error(s"Must be all alphanumeric characters"),
  str => str.forall(_.isLetterOrDigit)
)

def contains(char: Char): Predicate[Errors, String] = Predicate.lift(
  error(s"Must contain the character $char"),
  str => str.contains(char)
)

def containsOnce(char: Char): Predicate[Errors, String] = Predicate.lift(
  error(s"Must contain the character $char only once"),
  str => str.filter(c => c == char).size == 1
)

val checkUsername: Check[String, String] = checkPred(longThan(3) and alphanumeric)

val splitEmail: Check[String, (String, String)] = check(_.split('@') match {
  case Array(name, domain) =>
    Right((name, domain))
  case other =>
    Left(error("Must contain a single @ character"))
})

val checkLeft: Check[String, String] = checkPred(longThan(0))

val checkRight: Check[String, String] = checkPred(longThan(3) and contains('.'))

val joinEmail: Check[(String, String), String] = check { case (l, r) =>
  (checkLeft(l) |@| checkRight(r)).map ( _ + "@" + _)
}

val checkEmail: Check[String, String] = splitEmail andThen joinEmail

final case class User(username: String, email: String)

def createUser(username: String, email: String): Either[Errors, User] =
  (checkUsername.run(username) |@| checkEmail.run(email)).map(User)

createUser("Noel", "noel@underscore.io")
createUser("James", "dave@underscore.io@nba")

sealed trait Structure[E]

final case class Or[E](messages: List[Structure[E]]) extends Structure[E]
final case class And[E](messages: List[Structure[E]]) extends Structure[E]
final case class Not[E](messages: List[Structure[E]]) extends Structure[E]
final case class Pure[E](message: E) extends Structure[E]
