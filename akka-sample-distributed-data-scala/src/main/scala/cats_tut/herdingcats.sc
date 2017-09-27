import simulacrum._

import scala.annotation.tailrec
import scala.language.higherKinds

trait Monoid[A] {
  def mzero: A
  def mappend(a1: A, a2: A): A
}

object Monoid {
  //val syntax = ops
  implicit val IntMonoid: Monoid[Int] = new Monoid[Int] {
    override def mappend(a1: Int, a2: Int): Int = a1 + a2

    override def mzero: Int = 0
  }

  implicit val StringMonoid: Monoid[String] = new Monoid[String] {
    override def mappend(a1: String, a2: String): String = a1 + a2

    override def mzero: String = ""
  }
}

trait FoldLeft[F[_]]{
  def foldLeft[A, B](xs: F[A], b: B, f: (B, A) => B): B
}

object FoldLeft {
  implicit val FoldLeftList: FoldLeft[List] = new FoldLeft[List] {
    override def foldLeft[A, B](xs: List[A], b: B, f: (B, A) => B): B = xs.foldLeft(b)(f)
  }
}

def sum[M[_]: FoldLeft, A: Monoid](xs: M[A]): A = {
  val m = implicitly[Monoid[A]]
  val fl = implicitly[FoldLeft[M]]
  fl.foldLeft(xs, m.mzero, m.mappend)
}

sum(List(1, 3, 5, 7, 9))
sum(List("a", "bc", "de"))

/*import Monoid.syntax._

3 |+| 4

"James" |+| " " |+| "Wade"*/

import cats._
import cats.data._
import cats.implicits._

1.some
1.some.orEmpty
none.orEmpty

1 == 1
1 === 1
1 == "foo"

1 tryCompare(1)
1 tryCompare(2)
1 tryCompare(0)

case class Person(name: String)
case class Car(model: String)

implicit val personShow = Show.show[Person](_.name)
Person("james").show
Person("james").toString


implicit val carShow = Show.fromToString[Car]
Car("CR-V").show

sealed trait TrafficLight
object TrafficLight {
  case object Red extends TrafficLight
  case object Yellow extends TrafficLight
  case object Green extends TrafficLight

  def red: TrafficLight = Red
  def yellow: TrafficLight = Yellow
  def green: TrafficLight = Green
}

implicit val trafficLightEq: Eq[TrafficLight] = new Eq[TrafficLight] {
  override def eqv(x: TrafficLight, y: TrafficLight): Boolean = x == y
}

TrafficLight.red === TrafficLight.yellow

/*@typeclass trait CanTruthy[A] { self =>
  /** Return true, if `a` is truthy. */
  def truthy(a: A): Boolean
}

object CanTruthy {
  def fromTruthy[A](f: A => Boolean): CanTruthy[A] = new CanTruthy[A] {
    /** Return true, if `a` is truthy. */
    override def truthy(a: A): Boolean = f(a)
  }
}

implicit val intCanTruthy: CanTruthy[Int] = CanTruthy.fromTruthy {
  case 0 => false
  case 1 => true
}*/

//import CanTruthy.ops._
//10.truthy

case class Disjunction(val unwrap: Boolean) //extends AnyVal
object Disjunction {
  @inline def apply(b: Boolean): Disjunction = new Disjunction(b)

  implicit val disjunctionMonoid: cats.Monoid[Disjunction] = new cats.Monoid[Disjunction] {
    override def combine(a1: Disjunction, a2: Disjunction): Disjunction =
      Disjunction(a1.unwrap || a2.unwrap)

    override def empty: Disjunction = Disjunction(false)
  }

  implicit val disjunctionEq: Eq[Disjunction] = new Eq[Disjunction] {
    override def eqv(x: Disjunction, y: Disjunction): Boolean =
      x.unwrap == y.unwrap
  }
}

val x1 = Disjunction(true) |+| Disjunction(false)
x1.unwrap

List(1, 3, 5).foldMap(x => (x * 2) + ",")(cats.Monoid[String])

type Birds = Int
case class Pole(left: Birds, right: Birds) {
  def landLeft(n: Birds): Option[Pole] = {
    if (math.abs((left + n) - right) < 4) copy(left = left + n).some
    else none[Pole]
  }

  def landRight(n: Birds): Option[Pole] =
    if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
    else none[Pole]
}

val lbl = Monad[Option].pure(Pole(0, 0)).>>= ({ _.landLeft(1) }).>> (none[Pole]).>>= ({ _.landRight(1) })
val lb2 = (Monad[Option].pure(Pole(0, 0)) >>= { _.landLeft(1) }) >> none[Pole] >>= { _.landRight(1) }

def routine: Option[Pole] = for {
  start <- Monad[Option].pure(Pole(0, 0))
  first <- start.landLeft(2)
  second <- first.landRight(2)
  third <- second.landLeft(1)
} yield third

routine

assert { (Monad[Option].pure(3) >>= { x => (x + 100000).some }) === ({ (x: Int) => (x + 100000).some})(3)}

val english = Map(1 -> "one", 3 -> "three", 10 -> "ten")
(1 to 50).toList mapFilter { english.get }

case class KnightPos(c: Int, r: Int)

object KnightPos {
  class KnightPostOps(val p: KnightPos) {
    def move: List[KnightPos] = for {
      KnightPos(c2, r2) <- List(
        KnightPos(p.c + 2, p.r - 1),
        KnightPos(p.c + 2, p.r + 1),
        KnightPos(p.c - 2, p.r - 1),
        KnightPos(p.c - 2, p.r + 1),
        KnightPos(p.c + 1, p.r - 2),
        KnightPos(p.c + 1, p.r + 2),
        KnightPos(p.c - 1, p.r - 2),
        KnightPos(p.c - 1, p.r + 2)
      ) if (((1 to 8).toList contains c2) && ((1 to 8).toList contains r2))
    } yield KnightPos(c2, r2)

    def in3: List[KnightPos] = for {
      first <- move
      second <- first.move
      third <- second.move
    } yield third

    def canReachIn3(end: KnightPos): Boolean = in3 contains(end)
  }

  implicit def ops(p: KnightPos): KnightPostOps = new KnightPostOps(p)

}

KnightPos(6, 2) move

KnightPos(6, 2) canReachIn3  KnightPos(6, 1)
KnightPos(6, 2) canReachIn3  KnightPos(7, 3)

val w = Writer("Smallish gang.", 3)
val v = Writer.value[String, Int](3)
val l = Writer.tell[String]("Log something")

w.run

v.run
l.run

def logNumber(x: Int): Writer[List[String], Int] =
  Writer(List("Go number: " + x.show), 3)

def multWithLog: Writer[List[String], Int] = for {
  a <- logNumber(3)
  b <- logNumber(5)
} yield a * b

multWithLog.run

def gcd(a: Int, b: Int): Writer[List[String], Int] = {
  if (b == 0) for {
    x <- Writer.tell(List("Finished with " + a.show))
  } yield a
  else
    Writer.tell(List(s"${a.show} mod ${b.show} = ${(a % b).show}")) flatMap { _ => gcd(b, a % b) }
}

gcd(12, 16).run

def vectorFinalCountDown(x: Int): Writer[Vector[String], Int] = {
  @tailrec def doFinalCountDown(x: Int, w: Writer[Vector[String], Int]) : Writer[Vector[String], Int] = x match {
    case 0 => w >>= { xx => Writer(Vector(x.show), xx)}
    case x => doFinalCountDown(x - 1, w >>= { xx =>
      Writer(Vector(x.show), x |+| xx)
    })
  }

  val t0 = System.currentTimeMillis()
  val r = doFinalCountDown(x, Writer(Vector[String](), 0))
  val t1 = System.currentTimeMillis()
  r >>= { rs => Writer(Vector((t1 - t0).show + "msec"), rs) }
}

def listFinalCountDown(x: Int): Writer[List[String], Int] = {
  import annotation.tailrec
  @tailrec def doFinalCountDown(x: Int, w: Writer[List[String], Int]): Writer[List[String], Int] = x match {
    case 0 => w >>= { xx => Writer(List(x.show), xx) }
    case x => doFinalCountDown(x - 1, w >>= { xx =>
      Writer(List(x.show), x |+| xx)
    })
  }

  val t0 = System.currentTimeMillis
  val r = doFinalCountDown(x, Writer(List[String](), 0))
  val t1 = System.currentTimeMillis
  r >>= { rs => Writer(List((t1 - t0).show + " msec"), rs) }
}
vectorFinalCountDown(10000).run._1.last
listFinalCountDown(10000).run._1.last

vectorFinalCountDown(10).run

val f = (_: Int) * 2
val g = (_: Int) + 10

(g map f)(8)

val h = (f |@| g) map { _ + _ }
f |@| g
h(4)

val addStuff: Int => Int = for {
  a <- (_: Int) * 2
  b <- (_: Int) + 10
} yield a + b
addStuff(4)
