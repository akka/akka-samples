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
sealed trait CheckValidated[E, A] {
  def and(that: CheckValidated[E, A]): CheckValidated[E, A] = AndValidated(this, that)

  def or(that: CheckValidated[E, A]): CheckValidated[E, A] = this match {
    case PureValidated(func) => PureValidated { a => func(a) }
    case AndValidated(self, other) =>
      self.or(other)
  }

  def apply(a: A)(implicit s: Semigroup[E]): Validated[E, A] = this match {
    case PureValidated(f) =>
      f(a)
    case AndValidated(self, other) =>
      (self(a) |@| other(a)).map((_, _) => a)
  }
}

final case class AndValidated[E, A](self: CheckValidated[E, A], other: CheckValidated[E, A]) extends CheckValidated[E, A]
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



