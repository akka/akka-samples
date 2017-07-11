package advancedscala.chapter01

import cats.Show
import cats.instances.int._
import cats.instances.string._
import cats.syntax.show._

import cats.Eq
import cats.syntax.eq._
import cats.instances.option._
/**
  * Created by liaoshifu on 2017/6/1
  */
trait Printable[A] {
  def format(value: A): String
}

object Printable {
  def format[A](value: A)(implicit printer: Printable[A]) = printer.format(value)

  def print[A](value: A)(implicit printer: Printable[A]): Unit =
    println(printer.format(value))
}

object PrintableInstances {
  implicit object StringPrintable extends Printable[String] {
    override def format(value: String): String = value
  }

  implicit object IntPrintable extends Printable[Int] {
    override def format(value: Int): String = value.toString
  }

  implicit object CatPrintable extends Printable[Cat] {
    override def format(value: Cat): String = s"${value.name} is a ${value.age} year-old ${value.color} cat."
  }
}

object PrintableApp {
  def main(args: Array[String]): Unit = {
    import PrintableInstances._
    import Printable._
    
    println(s"The result of String format is ${format("who am i")}")
    print("James....")
    println(s"The result of Int format is ${format(13579)}")
    print(2468)

    val cat = Cat("Wade", 30, "Red")
    println(s"The cat format is: ${format(cat)}")
  }
}

case class Cat(
              name: String,
              age: Int,
              color: String
              )

object Cat {
  implicit val catShow = Show.show[Cat] { cat =>
    val name = cat.name.show
    val age = cat.age.show
    val color = cat.color.show
    s"$name is a $age year-old $color cats."
  }

  implicit val catEq = Eq.instance[Cat] { (c1, c2) =>
    val eqName = c1.name === c2.name
    val eqAge = c1.age === c2.age
    val eqColor = c1.color === c2.color

    eqName && eqAge && eqColor
  }
}
object PrintableSyntax {
  implicit class PrintOps[A](value: A) {
    def format(implicit printer: Printable[A]): String = printer.format(value)

    def print(implicit printer: Printable[A]): Unit =
      println(printer.format(value))
  }
}

object PrintableSyntaxApp {
  def main(args: Array[String]): Unit = {
    import PrintableSyntax._
    import PrintableInstances._
    
    val cat = Cat("Wade", 33, "Red")
    println(s"the Cat format is: ${cat.format}")
    cat.print
  }
}

object ShowApp {
  def main(args: Array[String]): Unit = {
    println(Cat("Garfield", 35, "ginger and black").show)

    val cat1 = Cat("Garfield", 35, "orange and black")
    val cat2 = Cat("Heathliff", 30, "orange and black")

    println(cat1 === cat2)
    
    val oc1 = Option(cat1)
    val oc2 = Option(cat2)
    println(oc1 === oc2)
    println(oc1 =!= oc2)
  }
}