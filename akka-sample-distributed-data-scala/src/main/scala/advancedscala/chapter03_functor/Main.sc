
import cats.functor.Contravariant
import cats.instances.function._
import cats.syntax.functor._

import scala.util.Try

val func1 = (x: Int) => x.toDouble
val func2 = (y: Double) => y * 2

val func3 = func1.map(func2)
func3(2)
val func4 = func1 andThen func2
func4(2)

import cats.Functor
import cats.instances.option._
import cats.instances.list._

val list1 = List(1, 2, 3)
val list2 = Functor[List].map(list1)(_ * 2)

val option1 = Option(123)
val option2 = Functor[Option].map(option1)(_.toString)

val func = (x: Int) => x + 1

val lifted = Functor[Option].lift(func)

lifted(Option(20))

import scala.concurrent._
implicit def futureFunctor(implicit ec: ExecutionContext) =
  new Functor[Future] {
    def map[A, B](value: Future[A])(func: A => B): Future[B] =
      value.map(func)
  }

sealed trait Tree[+A]
final case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]
final case class Leaf[A](value: A) extends Tree[A]

implicit val treeFunctor = new Functor[Tree] {

  override def map[A, B](fa: Tree[A])(f: A => B): Tree[B] = fa match {
    case Branch(l, r) => Branch(left = map(l)(f), right = map(r)(f))
    case Leaf(v) => Leaf(f(v))
  }
}

def branch[A](left: Tree[A], right: Tree[A]): Tree[A] = Branch(left, right)

def leaf[A](value: A): Tree[A] = Leaf(value)

leaf(50).map(1 + )
branch(Leaf(10), Leaf(20)).map(2 * )

trait Printable[A] {
  def format(value: A): String

  def contramap[B](f: B => A): Printable[B] = {
    val self = this
    new Printable[B] {
      override def format(value: B) = self.format(f(value))
    }
  }
}

def format[A](value: A)(implicit p: Printable[A]): String = p.format(value)

implicit val stringPrintable = new Printable[String] {
  override def format(value: String) = "\"" + value + "\""
}

implicit val intPrintable = new Printable[Int] {
  override def format(value: Int) = s"Int: $value"
}

implicit val booleanPrintable = new Printable[Boolean] {
  override def format(value: Boolean) = value match {
    case true => "yes"
    case false => "no"
  }
}

format("abc")
format(30)
format(true)
format(false)

final case class Box[A](value: A)

/*implicit def boxPrintable[A](implicit p: Printable[A]) = new Printable[Box[A]] {
  override def format(value: Box[A]): String = p.format(value.value)
}*/
implicit def boxPrintable[A](implicit p: Printable[A]) = p.contramap[Box[A]](_.value)

format(Box("hello world"))
format(Box(true))

trait Codec[A] {
  def encode(value: A): String
  def decode(s: String): Option[A]

  def imap[B](dec: A => B, enc: B => A): Codec[B] = {
    val self = this
    new Codec[B] {
      override def encode(value: B): String = self.encode(enc(value))

      override def decode(s: String): Option[B] = self.decode(s).map(dec)
    }
  }
}

def encode[A](value: A)(implicit c: Codec[A]): String =
  c.encode(value)

def decode[A](s: String)(implicit c: Codec[A]): Option[A] = c.decode(s)

implicit val intCodec = new Codec[Int] {
  override def encode(value: Int) = value.toString
  override def decode(s: String) = Try(s.toInt).toOption
}

implicit def boxCode[A](implicit c: Codec[A]) = c.imap[Box[A]](Box(_), _.value)

encode(Box(123))

decode[Box[Int]]("123")

import cats.Show
import cats.functor.Contravariant._
import cats.instances.string._
val showString = Show[String]
val showSymbol = Contravariant[Show].contramap(showString)((sym: Symbol) => s"'${sym.name}")

showSymbol.show('Jame)
