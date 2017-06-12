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
}

final case class Addition(left: Expression, right: Expression) extends Expression
final case class Subtraction(left: Expression, right: Expression) extends Expression
final case class Number(value: Double) extends Expression
