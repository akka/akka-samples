package monads.logo

import cats.{Id, ~>}
import monads.logo.Logo.Instruction
import Logo._

object InterpreterId extends (Instruction ~> Id) {
  import Computations._

  override def apply[A](fa: Instruction[A]) = fa match {
    case Forward(p, l) => forward(p, l)
    case Backward(p, l) => backward(p, l)
    case RotateLeft(p, d) => left(p, d)
    case RotateRight(p, d) => right(p, d)
    case ShowPosition(p) => println(s"showing position $p")
  }
}
