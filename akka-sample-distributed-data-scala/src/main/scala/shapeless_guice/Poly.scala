package shapeless_guice

import shapeless._

/*
trait Case[P, A] {
  type Result

  def apply(a: A): Result
}
trait Poly {
  def apply[A](arg: A)(implicit cse: Case[this.type , A]): cse.Result = cse.apply(arg)
}


object myPoly extends Poly1 {
  implicit def intCase = new Case[this.type, Int] {
    override type Result = Double
    def apply(num: Int): Double = num / 2.0
  }

  implicit def stringCase = new Case[this.type , String] {
    override type Result = Int

    override def apply(a: String): Int = a.length
  }
}
*/
object myPoly extends Poly1 {
  implicit val intCase: Case.Aux[Int, Double] = at(num => num / 2.0)
  implicit val stringCase: Case.Aux[String, Int] = at(str => str.length)
  implicit val booleanCase: Case.Aux[Boolean, String] = at(bool => if (bool) "yes" else "no")
}

object multiply extends Poly2 {
  implicit val intIntCase: Case.Aux[Int, Int, Int] = at((a, b) => a * b)
  implicit val intStrCase: Case.Aux[Int, String, String] = at((a, b) => b * 3)
}

import scala.math.Numeric

object total extends Poly1 {
  implicit def base[A](implicit num: Numeric[A]): Case.Aux[A, Double] = at(num.toDouble)
  implicit def option[A](implicit num: Numeric[A]): Case.Aux[Option[A], Double] = at(opt => opt.map(num.toDouble).getOrElse(0.0))
  implicit def list[A](implicit num: Numeric[A]): Case.Aux[List[A], Double] = at(ls => num.toDouble(ls.sum))// at(ls => ls.map(num.toDouble).sum)
}

object sizeOf extends Poly1 {
  implicit val intCase: Case.Aux[Int, Int] = at(identity)

  implicit val stringCase: Case.Aux[String, Int] = at(_.length)

  implicit val boolean: Case.Aux[Boolean, Int] = at(bool => if (bool) 1 else 0)

}

object valueAndSizeOf extends Poly1 {
  implicit val intCase: Case.Aux[Int, Int :: Int :: HNil] = at(num => num :: num :: HNil)
  implicit val stringCase: Case.Aux[String, String :: Int :: HNil] = at(str => str :: str.length :: HNil)
  implicit val booleanCase: Case.Aux[Boolean, Boolean :: Int :: HNil] = at(bool => bool :: (if (bool) 1 else 0) :: HNil )
}
object PolyApp {
  def main(args: Array[String]): Unit = {
    println(myPoly.apply(20))
    println(myPoly.apply("James"))
    println(myPoly.apply(true))
    println(multiply(2, 3))
    println(multiply(3, "James"))
    println(total(10))
    println(total(Option(9)))
    println(total(List(20, 2, 200)))

    val a: Double = myPoly.apply[Int](123)
    println(a)

    val hlist = 10 :: "James" :: 3 :: "Wade" :: true :: HNil
    val s1 = hlist.map(sizeOf)

    println(s1)

    val s2 = hlist.flatMap(valueAndSizeOf)
    println(s2)
  }
}
