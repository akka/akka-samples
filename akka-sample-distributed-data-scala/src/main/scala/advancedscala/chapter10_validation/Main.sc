import cats.Semigroup
import cats.data.Validated
import cats.instances.list._
import cats.instances.string._
import cats.syntax.monoid._
import cats.syntax.either._
import cats.syntax.semigroup._

//type Check[E, A] = A => Either[E, A]

sealed trait Check[E, A] {
  def apply(a: A)(implicit s: Semigroup[E]): Either[E, A] = this match {
    case Pure(f) => f(a)
    case And(self, other) =>
      (self(a), other(a)) match {
        case (Left(e1), Left(e2)) => s.combine(e1, e2).asLeft
        case (Left(e), Right(a)) => e.asLeft
        case (Right(a), Left(e)) => e.asLeft
        case (Right(a1), Right(a2)) => a.asRight
      }
  }

  def and(that: Check[E, A]): Check[E, A] = And(this, that)
}

final case class And[E, A](self: Check[E, A], other: Check[E, A]) extends Check[E, A]
final case class Pure[E, A](func: A => Either[E, A]) extends Check[E, A]

//List("a", "b") |+| List("d", "e", "f")

val semigroupList = Semigroup[List[String]]

semigroupList.combine(List("James", "Wade", "Bosh"), List("Haden", "Paul", "Athony"))

final case class CheckF[E, A](func: A => Either[E, A]) {
  def apply(a: A): Either[E, A] = func(a)

  def and(that: CheckF[E, A])(implicit s: Semigroup[E]): CheckF[E, A] =
    CheckF { a =>
      (this(a), that(a)) match {
        case (Left(e1), Left(e2)) =>
          /*(e1 |+| e2).asLeft*/  Left(s.combine(e1, e2))
        case (Left(e1), Right(a2)) =>
          e1.asLeft   //Left(e1)
        case (Right(a1), Left(e2)) =>
          e2.asLeft   // Left(e2)
        case (Right(a1), Right(a2)) =>
          a.asRight
      }
    }
}

val a: CheckF[List[String], Int] = CheckF { v =>
  if (v > -2) v.asRight
  else List("Must be > -2").asLeft
}

val b: CheckF[List[String], Int] = CheckF { v =>
  if (v < 2) v.asRight
  else List("Must be < 2").asLeft
}

val check = a and b
check(1)
check(3)
check(-4)
check(0)

val a1: Check[List[String], Int] = Pure { v =>
  if (v > 2) v.asRight
  else List("Must be > 2").asLeft
}

val b1: Check[List[String], Int] = Pure { v =>
  if (v < -2) v.asRight
  else List("Must be < -2").asLeft
}

val check1 = a1 and b1
check1(1)
check1(3)
check1(-4)
check1(0)

import cats.syntax.validated._
import cats.syntax.cartesian._
import cats.data.Validated._

sealed trait CheckValidated[E, A] {
  def and(that: CheckValidated[E, A]): CheckValidated[E, A] = AndValidated(this, that)

  def or(that: CheckValidated[E, A]): CheckValidated[E, A] =
    OrValidated(this, that)

  def apply(a: A)(implicit s: Semigroup[E]): Validated[E, A] = this match {
    case PureValidated(f) =>
      f(a)
    case AndValidated(self, other) =>
      (self(a) |@| other(a)).map((_, _) => a)
    case OrValidated(self, other) =>
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

final case class AndValidated[E, A](self: CheckValidated[E, A], other: CheckValidated[E, A]) extends CheckValidated[E, A]
final case class OrValidated[E, A](self: CheckValidated[E, A], other: CheckValidated[E, A]) extends CheckValidated[E, A]
final case class PureValidated[E, A](func: A => Validated[E, A]) extends CheckValidated[E, A]

val av: CheckValidated[String, Int] = PureValidated { v =>
  if (v > 2) v.valid
  else "Must be > 2".invalid
}

val bv: CheckValidated[String, Int] = PureValidated { v =>
  if (v < -2) v.valid
  else "Must be < -2".invalid
}

val checkv = av and bv
checkv(1)
checkv(3)
checkv(-4)
checkv(0)

val checkOr = av or bv
checkOr(1)
checkOr(3)
checkOr(-4)
checkOr(0)

sealed trait Predicate[E, A] {
  import Predicate._

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

sealed trait CheckV[E, A, B] {

  import CheckV._

  def apply(in: A)(implicit s: Semigroup[E]): Validated[E, B]

  def map[C](f: B => C): CheckV[E, A, C] =
    CheckMap[E, A, B, C](this, f)

  def flatMap[C](f: B => CheckV[E, A, C]): CheckV[E, A, C] =
    CheckFlatMap[E, A, B, C](this, f)

  def andThen[C](that: CheckV[E, B, C]): CheckV[E, A, C] =
    CheckAndThen(this, that)
}

object CheckV {
  
  final case class CheckMap[E, A ,B, C](
                                         check: CheckV[E, A, B],
                                         func: B => C
                                       ) extends CheckV[E, A, C] {
    def apply(in: A)(implicit s: Semigroup[E]): Validated[E, C] =
      check(in).map(func)

  }

  final case class CheckFlatMap[E, A, B, C](
                                             check: CheckV[E, A, B],
                                             func: B => CheckV[E, A, C]
                                           ) extends CheckV[E, A, C] {
    def apply(in: A)(implicit s: Semigroup[E]): Validated[E, C] =
      check(in).withEither(e => e.flatMap(b => func(b)(in).toEither))
  }

  final case class CheckAndThen[E, A, B, C](
                                             check1: CheckV[E, A, B],
                                             check2: CheckV[E, B, C]
                                           ) extends CheckV[E, A, C] {
    def apply(in: A)(implicit s: Semigroup[E]): Validated[E, C] =
      check1(in).withEither(_.flatMap(b => check2(b).toEither))
  }

  final case class CheckPure[E, A, B](
                                     func: A => Validated[E, B]
                                     ) extends CheckV[E, A, B] {
    def apply(in: A)(implicit s: Semigroup[E]): Validated[E, B] =
      func(in)
  }

  final case class PurePredicate[E, A](
                                    pred: Predicate[E, A]
                                  ) extends CheckV[E, A, A] {
    def apply(in: A)(implicit s: Semigroup[E]): Validated[E, A] =
      pred(in)

    def apply[E, A](pred: Predicate[E, A]): CheckV[E, A, A] =
      PurePredicate(pred)

    def apply[E, A, B](func: A => Validated[E, B]): CheckV[E, A, B] =
      CheckPure(func)
  }

}


import cats.data._
type Errors = NonEmptyList[String]

def error(s: String): NonEmptyList[String] = NonEmptyList(s, Nil)

def longThan(n: Int): Predicate[Errors, String] = Predicate.lift(
  error(s"Must be longer than $n characters"),
  str => str.size > n
)

def alphanumeric: Predicate[Errors, String] = Predicate.lift(
  error(s"Must be all alphanumeric characters"),
  str => str.forall(_.isLetterOrDigit)
)

def contains(char: Char): Predicate[Errors, String] = Predicate.lift(
  error(s"Must be contain the charater $char"),
  str => str.contains(char)
)

def containsOnce(char: Char): Predicate[Errors, String] = Predicate.lift(
  error(s"Must contain the character $char only once"),
  str => str.filter(c => c == char).size == 1
)

import cats.syntax.cartesian._
import cats.syntax.validated._
import CheckV._

val checkUsername: CheckV[Errors, String, String] = PurePredicate(longThan(3) and alphanumeric)

val splitEmail: CheckV[Errors, String, (String, String)] =
  CheckPure(_.split('@') match {
    case Array(name, domain) =>
      (name, domain).validNel[String]
    case other =>
      "Must contain a single @ character".invalidNel[(String, String)]
  })

val checkLeft: CheckV[Errors, String, String] =
   PurePredicate(longThan(0))

val checkRight: CheckV[Errors, String, String] =
  PurePredicate(longThan(3) and contains('.'))

val joinEmail: CheckV[Errors, (String, String), String] =
  CheckPure { case (l, r) =>
    (checkLeft(l) |@| checkRight(r)).map(_ + "@" + _)
  }

val checkEmail: CheckV[Errors, String, String] = splitEmail andThen joinEmail

final case class User(username: String, email: String)

def createUser(
              username: String,
              email: String
              ): Validated[Errors, User] =
  (checkUsername(username) |@| checkEmail(email)).map(User)

createUser("Noel", "noel@underscore.io")
createUser("", "dave@underscore@io")
