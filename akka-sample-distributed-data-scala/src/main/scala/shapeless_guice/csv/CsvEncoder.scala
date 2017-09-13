package shapeless_guice
package csv

import shapeless._
import shapeless.ops.hlist.{IsHCons, Last}

trait CsvEncoder[A] {
  def encode(value: A): List[String]
}

object CsvEncoder {

  def apply[A](implicit enc: CsvEncoder[A]): CsvEncoder[A] = enc

  def createEncoder[A](func: A => List[String]): CsvEncoder[A] = new CsvEncoder[A] {
    override def encode(value: A): List[String] = func(value)
  }

  implicit val stingEncoder: CsvEncoder[String] = createEncoder(str => List(str))

  implicit val intEncoder: CsvEncoder[Int] = createEncoder(num => List(num.toString))

  implicit val booleanEncoder: CsvEncoder[Boolean] = createEncoder(b => List(if (b) "yes" else "no"))

  implicit val doubleEncoder: CsvEncoder[Double] = createEncoder(d => List(d.toString))

  implicit def listEncoder[A](implicit enc: CsvEncoder[A]): CsvEncoder[List[A]] = createEncoder(ls => ls.flatMap(l => enc.encode(l)))

  implicit val hnilEncoder: CsvEncoder[HNil] = createEncoder(hnil => Nil)

  implicit def hlistEncoder[H, T <: HList](
                                          implicit
                                          hEncoder: Lazy[CsvEncoder[H]],
                                          tEncoder: CsvEncoder[T]
                                          ): CsvEncoder[H :: T] = createEncoder {
    case h :: t =>
      hEncoder.value.encode(h) ++ tEncoder.encode(t)
  }

  implicit val cniEncoder: CsvEncoder[CNil] = createEncoder(cnil => throw new Exception("Inconceivable!"))

  implicit def coproductEncoder[H, T <: Coproduct](
                                                  implicit
                                                  hEncoder: Lazy[CsvEncoder[H]],
                                                  tEncoder: CsvEncoder[T]
                                                  ): CsvEncoder[H :+: T] = createEncoder {
    case Inl(h) => hEncoder.value.encode(h)
    case Inr(t) => tEncoder.encode(t)
  }

  implicit def genericEncoder[A, R](
                                implicit
                                gen: Generic.Aux[A, R],
                                enc: Lazy[CsvEncoder[R]]
                                ): CsvEncoder[A] = createEncoder(a => enc.value.encode(gen.to(a)))

  def genericCsv(gen: String :: Int :: Boolean :: HNil): List[String] =
    List(gen(0), gen(1).toString, gen(2).toString)

  def writeCsv[A](values: List[A])(implicit enc: CsvEncoder[A]): String =
    values.map(value => enc.encode(value).mkString(",")).mkString("\n")

  def getWrappedValue[A, Repr <: HList, Head](in: A)(
                                             implicit
                                             gen: Generic.Aux[A, Repr],
                                             isHCons: IsHCons.Aux[Repr, Head, HNil]
  ): Head = gen.to(in).head

  /*implicit val employeeEncode: CsvEncoder[Employee] = new CsvEncoder[Employee] {
    override def encode(value: Employee): List[String] = List(
      value.name, value.number.toString, if (value.manager) "yes" else "no"
    )
  }*/

  /*implicit val iceCreamEncoder: CsvEncoder[IceCream] = {
    val gen = Generic[IceCream]
    val enc = CsvEncoder[gen.Repr]
    createEncoder(iceCream => enc.encode(gen.to(iceCream)))
  }
  */
  /*implicit def pairEncode[A, B](
                                 implicit
                                 aEncoder: CsvEncoder[A],
                                 bEncoder: CsvEncoder[B]
                               ): CsvEncoder[(A, B)] = new CsvEncoder[(A, B)] {
    override def encode(value: (A, B)): List[String] = {
      val (a, b) = value
      aEncoder.encode(a) ++ bEncoder.encode(b)
    }
  }
*/

}

object CsvApp {

  val employees: List[Employee] = List(
    Employee("Bill", 1, true),
    Employee("Peter", 2, false),
    Employee("Milton", 3, false)
  )

  val iceCreams: List[IceCream] = List(
    IceCream("Sundae", 1, false),
    IceCream("Cornetto", 0, true),
    IceCream("Banana Split", 0, false)
  )

  val persons: List[Person] = List(
    Person("James", 33, List("Cle", "Mia")), Person("Wade", 35, List("Mia", "Chi")), Person("Bosh", 34, List("Dol", "Mia"))
  )

  val shapes: List[Shape] = List(
    Rectangle(3.0, 4.0),
    Triangle(3.5, 5.6, 8.8),
    Circle(2.0)
  )

  def main(args: Array[String]): Unit = {
    import CsvEncoder._
    val employee = Employee("Dave", 123, false)
    val iceCream = IceCream("Sundae", 2, false)
    val genericEmployee = Generic[Employee].to(employee)
    val genericIceCream = Generic[IceCream].to(iceCream)

    val er = genericCsv(genericEmployee)
    println(er)

    val ir = genericCsv(genericIceCream)
    println(ir)

    val employee1 = Generic[Employee].from(genericIceCream)
    println(employee1)

    val employee2 = Generic[Employee].from("James" :: 33 :: true :: HNil)
    println(employee2)

    println(writeCsv(employees))
    println(writeCsv(iceCreams))
    println(writeCsv(employees zip iceCreams))

    val reprEncoder: CsvEncoder[String :: Int :: Boolean :: HNil] = implicitly

    val abc = reprEncoder.encode("abc" :: 123 :: true :: HNil)
    println(abc)

    println(writeCsv(persons))
    println(writeCsv(shapes))

    CsvEncoder[Tree[Int]]

    import scala.reflect.runtime.universe._
    println(reify(CsvEncoder[Int]))

    val last1 = Last[String :: Int :: HNil]
    println(last1("Foo" :: 123 :: HNil))

    println(getWrappedValue(Wrapper(42)))
  }
}
