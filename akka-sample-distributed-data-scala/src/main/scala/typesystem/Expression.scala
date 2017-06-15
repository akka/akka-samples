package typesystem

/**
  * Recursion Data
  */
sealed trait Expression {
  def eval: Double = this match {
    case Addition(l, r) => l.eval + r.eval
    case Subtraction(l, r) => l.eval - r.eval
    case Number(v) => v
  }

  def eval2: Sum[String, Double] = this match {
    case Number(v) => Right(v)
    case SquareRoot(v) => v.eval2.flatMap { value =>
      if (value < 0) Left("Square root of negative number")
      else Right(math.sqrt(value))
    }
    case Division(l, r) => lift2(l, r, (a, b) => {
      if (b == 0) Left("Division by zero")
      else Right(a / b)
    })
    case Subtraction(l, r) => lift2(l, r, (a, b) => Right(a - b))
    case Addition(l, r) => lift2(l, r, (a, b) => Right(a + b))
  }

  def lift2(l: Expression, r: Expression, f: (Double, Double) => Sum[String, Double]) =
    l.eval2.flatMap { left =>
      r.eval2.flatMap { right =>
        f(left, right)
      }
    }
}

final case class Addition(left: Expression, right: Expression) extends Expression
final case class Subtraction(left: Expression, right: Expression) extends Expression
final case class Division(left: Expression, right: Expression) extends Expression
final case class SquareRoot(value: Expression) extends Expression
final case class Number(value: Double) extends Expression

object ExpressionApp {
  def main(args: Array[String]): Unit = {
    assert(Addition(Number(1), Number(2)).eval2 == Right(3))
    assert(SquareRoot(Number(-1)).eval2 == Left("Square root of negative number"))
    assert(Division(Number(4), Number(0)).eval2 == Left("Division by zero"))
    assert(Division(Addition(Subtraction(Number(8), Number(6)), Number(2)), Number(2)).eval2 == Right(2.0))
  }
}
