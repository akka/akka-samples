package monads.logo

import cats.~>
import monads.logo.Logo._

/**
  *
  */
object InterpreterOpt extends (Instruction ~> Option) {
  import Computations._

  val nonNegative: (Position) => Option[Position] = {
    (p: Position) => if (p.x >=0 && p.y >= 0) Some(p) else None
  }

  override def apply[A](fa: Instruction[A]) = fa match {
    case Forward(p, l) => nonNegative(forward(p, l))
    case Backward(p, l) => nonNegative(backward(p, l))
    case RotateLeft(p, d) => Some(left(p, d))
    case RotateRight(p, d) => Some(right(p, d))
    case ShowPosition(p) => Some(println(s"showing position $p"))
  }
}
