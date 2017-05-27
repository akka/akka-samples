package monads.logo

import cats.data.Coproduct
import cats.free.Free
import cats.{Id, ~>}

/**
  * http://blog.scalac.io/2016/06/02/overview-of-free-monad-in-cats.html
  */
object Logo {

  type LogoApp[A] = Coproduct[Instruction, PencilInstruction, A]
  case class Position(x: Double, y: Double, heading: Degree)
  case class Degree(private val d: Int) {
    val value = d % 360
  }

  sealed trait Instruction[A]

  case class Forward(position: Position, length: Int) extends Instruction[Position]
  case class Backward(position: Position, length: Int) extends Instruction[Position]
  case class RotateLeft(position: Position, degree: Degree) extends Instruction[Position]
  case class RotateRight(position: Position, degree: Degree) extends Instruction[Position]
  case class ShowPosition(position: Position) extends Instruction[Unit]

  sealed trait PencilInstruction[A]
  case class PencilUp(position: Position) extends PencilInstruction[Unit]
  case class PencilDown(position: Position) extends PencilInstruction[Unit]

  def forward(pos: Position, l: Int): Free[Instruction, Position] = Free.liftF(Forward(pos, l))
  def backward(pos: Position, l: Int): Free[Instruction, Position] = Free.liftF(Backward(pos, l))
  def left(pos: Position, degree: Degree): Free[Instruction, Position] = Free.liftF(RotateLeft(pos, degree))
  def right(pos: Position, degree: Degree): Free[Instruction, Position] = Free.liftF(RotateRight(pos, degree))
  def showPosition(pos: Position): Free[Instruction, Unit] = Free.liftF(ShowPosition(pos))

  val program: (Position => Free[Instruction, Position]) = {
    start: Position =>
      for {
        p1 <- forward(start, 10)
        p2 <- right(p1, Degree(90))
        p3 <- forward(p2, 10)
        _ <- showPosition(p3)
      } yield p3
  }

  //type Id[A] = A

}


