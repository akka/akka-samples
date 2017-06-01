package advancedscala.chapter01

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