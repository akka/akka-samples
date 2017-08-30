package cats_tut.logo

import cats.{Id, ~>}
import Logo.{PencilDown, PencilUp}
import Logo.PencilInstruction

/**
  *
  */
object PenInterpreterId extends (PencilInstruction ~> Id) {
  override def apply[A](fa: PencilInstruction[A]): Id[A] = fa match {
    case PencilUp(p) => println(s"start drawing at $p")
    case PencilDown(p) => println(s"stop drawing at $p")
  }
}

object PenInterpreterOpt extends (PencilInstruction ~> Option) {
  override def apply[A](fa: PencilInstruction[A]): Option[A] = fa match {
    case PencilUp(p) => Some(println(s"start drawing at $p"))
    case PencilDown(p) => Some(println(s"stop drawing at $p"))
  }
}
