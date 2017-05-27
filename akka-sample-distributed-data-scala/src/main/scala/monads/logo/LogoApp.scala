package monads.logo

import cats.free.Free
import monads.logo.Logo._

import cats.implicits._

/**
  *
  */
object LogoApp {

  val programOpt: (Position => Free[Instruction, Unit]) = {
    s: Position =>
      for {
        p1 <- forward(s, 10)
        p2 <- right(p1, Degree(90))
        p3 <- forward(p2, 10)
        _ <- showPosition(p3)
        //p4 <- backward(p3, 20) // Here the computation stops, because result will be None
        //_ <- showPosition(p4)
      } yield ()
  }

  def main(args: Array[String]): Unit = {
    val startPosition = Position(0.0, 0.0, Degree(0))
    
    //val result = program(startPosition).foldMap(InterpreterId)
    //println(result.x)
    //println(result.y)
    //println(result.heading)
    val resultOpt = programOpt(startPosition).foldMap(InterpreterOpt)
  }
}
