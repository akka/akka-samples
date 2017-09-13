package shapeless_guice
package csv.solutions

import shapeless._

trait CsvEncoder[A] {
  val width: Int
  def encode(value: A): List[String]
}

object CsvEncoder {
  def pure[A](w: Int)(func: A => List[String]): CsvEncoder[A] = new CsvEncoder[A] {
    override def encode(value: A): List[String] = func(value)

    override val width: Int = w
  }

  implicit val stingEncoder: CsvEncoder[String] = pure(1)(str => List(str))

  implicit val intEncoder: CsvEncoder[Int] = pure(1)(num => List(num.toString))

  implicit val booleanEncoder: CsvEncoder[Boolean] = pure(1)(b => List(if (b) "yes" else "no"))

  implicit val doubleEncoder: CsvEncoder[Double] = pure(1)(d => List(d.toString))

  implicit def optionEncoder[A](implicit encoder: CsvEncoder[A]): CsvEncoder[Option[A]] =
    pure(encoder.width)(opt => opt.map(encoder.encode).getOrElse(List.fill(encoder.width)("")))

  implicit def listEncoder[A](implicit encoder: CsvEncoder[A]): CsvEncoder[List[A]] =
    pure(encoder.width)(s => s.flatMap(l => encoder.encode(l)))

  implicit val hnilEncoder: CsvEncoder[HNil] = pure(0)(hnil => Nil)

  implicit def hlistEncoder[H, T <: HList](
                                          implicit
                                          hEncoder: Lazy[CsvEncoder[H]],
                                          tEncoder: CsvEncoder[T]
                                          ): CsvEncoder[H :: T] =
    pure(hEncoder.value.width + tEncoder.width) {
      case h :: t =>
        hEncoder.value.encode(h) ++ tEncoder.encode(t)
    }

  implicit val cnilEncoder: CsvEncoder[CNil] = pure(0)(cnil => ???)

  implicit def coproductEncoder[H, T <: Coproduct](
                                                  implicit
                                                  hEncoder: Lazy[CsvEncoder[H]],
                                                  tEncoder: CsvEncoder[T]
                                                  ): CsvEncoder[H :+: T] =
    pure(hEncoder.value.width + tEncoder.width) {
      case Inl(h) => hEncoder.value.encode(h) ++ List.fill(tEncoder.width)("")
      case Inr(t) => List.fill(hEncoder.value.width)("") ++ tEncoder.encode(t)
    }

  implicit def genericEncoder[A, R](
                                   implicit
                                   gen: Generic.Aux[A, R],
                                   enc: Lazy[CsvEncoder[R]]
                                   ): CsvEncoder[A] =
    pure(enc.value.width)(a => enc.value.encode(gen.to(a)))
}


object Main extends Demo {

  def encodeCsv[A](value: A)(implicit enc: CsvEncoder[A]): List[String] = enc.encode(value)

  def writeCsv[A](values: List[A])(implicit enc: CsvEncoder[A]): String =
    values.map(value => enc.encode(value).mkString(",")).mkString("\n")

  val shapes: List[Shape] = List(
    Rectangle(1, 2),
    Triangle(2, 4, 6),
    Rectangle(5, 8),
    Circle(3),
    Triangle(3, 5, 7),
    Circle(6)
  )

  val optShapes: List[Option[Shape]] = List(
    Some(Rectangle(1, 2)),
    Some(Triangle(2, 4, 6)),
    None,
    Some(Rectangle(5, 8)),
    Some(Circle(3)),
    None,
    Some(Triangle(3, 5, 7)),
    Some(Circle(6))
  )

  val persons: List[Person] = List(
    Person("James", 33, List("Cle", "Mia")), Person("Wade", 35, List("Mia", "Chi")), Person("Bosh", 34, List("Dol", "Mia"))
  )

  println("Shapes " + shapes)
  println("Shapes as CSV: \n" + writeCsv(shapes))
  println("Optional shapes " + optShapes)
  println("Optional shapes as CSV: \n" + writeCsv(optShapes))

  println(writeCsv(persons))
}