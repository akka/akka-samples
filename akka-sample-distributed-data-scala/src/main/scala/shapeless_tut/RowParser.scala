package shapeless_tut

import shapeless.{::, Generic, HList, HNil}

import scala.reflect.ClassTag
import scala.util.Try

/**
  * https://meta.plasm.us/posts/2015/11/08/type-classes-and-generic-derivation/
  */
case class Person2(name: String, age: Double)
case class Book(title: String, author: String, year: Int)
case class Country(name: String, population: Int, area: Double)

trait RowParser[A] {
  def apply(s: String): Option[A]
}

object RowParser {

  def apply[A](s: String)(implicit parser: RowParser[A]): Option[A] = parser(s)

  /*val personParser: RowParser[Person2] = new RowParser[Person2] {
    def apply(s: String): Option[Person2] = s.split(",").toList match {
      case List(name, age) => Try(age.toDouble).map(Person2(name, _)).toOption
      case _ => None
    }
  }

  val bookParser: RowParser[Book] = new RowParser[Book] {
    def apply(s: String): Option[Book] = s.split(",").toList match {
      case List(title, author, year) =>
        Try(year.toInt).map(Book(title, author, _)).toOption
      case _ => None
    }
  }*/

  def createParser[A](func: String => Option[A]): RowParser[A] = new RowParser[A] {
    override def apply(s: String): Option[A] = func(s)
  }

  implicit val stringParser: RowParser[String] = createParser { s => Option(s.trim)}

  implicit val intParser: RowParser[Int] = createParser(s => Try(s.trim.toInt).toOption)

  implicit val doubleParser: RowParser[Double] = createParser(s => Try(s.trim.toDouble).toOption)

  implicit val hnilParser: RowParser[HNil] = createParser(s => if (s.isEmpty) Some(HNil) else None)

  implicit def hconsParser[H : RowParser, T <: HList : RowParser]: RowParser[H :: T] = createParser {
    s => s.split(",").toList match {
      case h +: t =>
        for {
          head <- implicitly[RowParser[H]].apply(h)
          tail <- implicitly[RowParser[T]].apply(t.mkString(","))
        } yield head :: tail
    }

  }

  implicit def genericParser[A, R <: HList](
                                  implicit
                                           gen: Generic.Aux[A, R],
                                  parser: RowParser[R]
                                  ): RowParser[A] = createParser { s =>
    val p = parser.apply(s)//
    p.map(gen.from)
  }
}

object ReflectiveRowParser {
  def apply[T: ClassTag](s: String): Option[T] = Try {
    val ctor = implicitly[ClassTag[T]].runtimeClass.getConstructors.head
    val paramsArray = s.split(",").map(_.trim)
    val paramsWithTypes = paramsArray.zip(ctor.getParameterTypes)

    val parameters = paramsWithTypes.map {
      case (param, cls) =>
        cls.getName match {
          case "int" => param.toInt.asInstanceOf[Object]
          case "double" => param.toDouble.asInstanceOf[Object]
          case _ =>
            val paramConstructor = cls.getConstructor(param.getClass)
            paramConstructor.newInstance(param).asInstanceOf[Object]
        }
    }

    ctor.newInstance(parameters: _*).asInstanceOf[T]
  }.toOption
}

object RowParseApp {
  
  def main(args: Array[String]): Unit = {
    println(ReflectiveRowParser[Person2]("Amy, 54.2"))
    println(ReflectiveRowParser[Person2]("Fred, 20.8"))
    println(ReflectiveRowParser[Book]("Hamlet, Shakespeare, 1600"))
    println(ReflectiveRowParser[Country]("Finland, 450000, 33820"))
    println(ReflectiveRowParser[Book]("Hamlet, James"))

    println(RowParser[Person2]("Amy, 54.2"))
    println(RowParser[Person2]("Fred, 20.8"))
    println(RowParser[Book]("Hamlet, Shakespeare, 1600"))
    println(RowParser[Country]("Finland, 450000, 33820"))
    println(RowParser[Book]("Hamlet, James"))
  }
}