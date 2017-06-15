package typesystem

/**
  * typeclass
  */
final case class Rational(numerator: Int, denominator: Int)

object Rational {
  implicit val ordering = Ordering.fromLessThan[Rational] { (x, y) =>
    (x.numerator.toDouble / x.denominator.toDouble) < (y.numerator.toDouble / y.denominator.toDouble)
  }
}
object ImplicitApp {
  def main(args: Array[String]): Unit = {
    assert(
      List(Rational(1, 2), Rational(3, 4), Rational(1, 3)).sorted ==  List(Rational(1, 3), Rational(1, 2), Rational(3, 4))
    )
  }
}
