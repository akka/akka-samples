package cats_tut.logo

import cats.data.Coproduct
import cats.free.{Free, Inject}
import cats.implicits._
import cats.{Id, ~>}

import scala.language.higherKinds

/**
  * https://blog.scalac.io/2016/06/02/overview-of-free-monad-in-cats.html
  */

case class Position(x: Double, y: Double, heading: Degree)
case class Degree(private val d: Int) {
  val value = d % 360
}

object Logo {

  type LogoApp[A] = Coproduct[Instruction, PencilInstruction, A]
  
  /**
    * Create an ADT representing your grammar
    * @tparam A
    */
  sealed trait Instruction[A]
  case class Forward(position: Position, length: Int) extends Instruction[Position]
  case class Backward(position: Position, length: Int) extends Instruction[Position]
  case class RotateLeft(position: Position, degree: Degree) extends Instruction[Position]
  case class RotateRight(position: Position, degree: Degree) extends Instruction[Position]
  case class ShowPosition(position: Position) extends Instruction[Unit]

  sealed trait PencilInstruction[A]
  case class PencilUp(position: Position) extends PencilInstruction[Unit]
  case class PencilDown(position: Position) extends PencilInstruction[Unit]
  
  /**
    * Free your ADT
    * 1. Create a type based on Free[_] and KVStoreA[_]
    * 2. Create smart constructors for KVStore[_] using liftF
    * 3. Build a program out of key-value DSL operations
    * 4. Build a compiler or interpreter for programs of DSL operations
    * 5. Execute our compiler program
    */


  /**
    * Step 1 type based on Free[_]
    */
  type FreeInstruction[A] = Free[Instruction, A]

  /**
    * Step 2 smarter constructor
    */
  import cats.free.Free._

  object MoveConstructor {
    def forward(pos: Position, l: Int): FreeInstruction[Position] = liftF(Forward(pos, l))
    def backward(pos: Position, l: Int): FreeInstruction[Position] = liftF(Backward(pos, l))
    def left(pos: Position, degree: Degree): FreeInstruction[Position] = liftF(RotateLeft(pos, degree))
    def right(pos: Position, degree: Degree): FreeInstruction[Position] = liftF(RotateRight(pos, degree))
    def show(pos: Position): FreeInstruction[Unit] = liftF(ShowPosition(pos))
  }


  /**
    * Step 3 program function
    */
  val program: (Position => FreeInstruction[Position]) = {
    start: Position =>
      import MoveConstructor._
      for {
        p1 <- forward(start, 10)
        p2 <- right(p1, Degree(90))
        p3 <- forward(p2, 10)
      } yield p3
  }

  val programOpt: (Position => FreeInstruction[Unit]) = {
    s: Position =>
      import MoveConstructor._
      for {
        p1 <- forward(s, 10)
        p2 <- right(p1, Degree(90))
        p3 <- forward(p2, 10)
        p4 <- backward(p3, 2)  // Here the computation stops, because result will be None
        _ <- show(p4)
      } yield ()
  }

  import dsl._

  def programMP(s: Position)(implicit M: Moves[LogoApp], P: PencilActions[LogoApp]): Free[LogoApp, Unit] = {
    import M._, P._

      for {
        p1 <- forward(s, 10)
        p2 <- right(p1, Degree(90))
        _ <- pencilUp(p2)
        p3 <- forward(p2, 10)
        _ <- pencilDown(p3)
        p4 <- forward(p3, 10)
        _ <- show(p4)
      } yield ()
  }

  /**
    * Step 4 Interpreter
    */
  object InterpreterId extends (Instruction ~> Id) {
    import cats_tut.logo.Computations._
    override def apply[A](fa: Instruction[A]): Id[A] = fa match {
      case Forward(p, length) => forward(p, length)
      case Backward(p, length) => backward(p, length)
      case RotateLeft(p, d) => left(p, d)
      case RotateRight(p, d) => right(p, d)
      case ShowPosition(p) => println(s"showing position $p")
    }
  }

  object InterpreterOpt extends (Instruction ~> Option) {
    import cats_tut.logo.Computations._

    val nonNegative: (Position => Option[Position]) = {
      p => Some(p) //if (p.x >= 0 && p.y >= 0) Some(p) else None
    }

    override def apply[A](fa: Instruction[A]): Option[A] = fa match {
      case Forward(p, length) => nonNegative(forward(p, length))
      case Backward(p, length) => nonNegative(backward(p, length))
      case RotateLeft(p, d) => nonNegative(left(p, d))
      case RotateRight(p, d) => nonNegative(right(p, d))
      case ShowPosition(p) => Some(println(s"showing position $p"))
    }
  }

  object dsl {
    class Moves[F[_]](implicit I: Inject[Instruction, F]) {
      def forward(pos: Position, l: Int): Free[F, Position] = inject(Forward(pos, l))
      def backward(pos: Position, l: Int): Free[F, Position] = inject(Backward(pos, l))
      def left(pos: Position, degree: Degree): Free[F, Position] = inject(RotateLeft(pos, degree))
      def right(pos: Position, degree: Degree): Free[F, Position] = inject(RotateRight(pos, degree))
      def show(pos: Position): Free[F, Unit] = inject(ShowPosition(pos))
    }

    object Moves {
      implicit def moves[F[_]](implicit I: Inject[Instruction, F]): Moves[F] = new Moves[F]()
    }

    class PencilActions[F[_]](implicit I: Inject[PencilInstruction, F]) {
      def pencilUp(p: Position): Free[F, Unit] = inject(PencilUp(p))
      def pencilDown(p: Position): Free[F, Unit] = inject(PencilDown(p))
    }

    object PencilActions {
      implicit def pencilActions[F[_]](implicit I: Inject[PencilInstruction, F]): PencilActions[F] = new PencilActions[F]()
    }
  }

  /**
    * Step 5 execute
    */
  def main(args: Array[String]): Unit = {
    import dsl._

    val startPosition = Position(0.0, 0.0, Degree(0))
    
    val r = program(startPosition).foldMap(InterpreterId)
    println(r)

    val ro = programOpt(startPosition).foldMap(InterpreterOpt)
    println(ro)

    val interpreter: LogoApp ~> Id = InterpreterId or PenInterpreterId

    val ri = programMP(startPosition).foldMap(interpreter)
    println(ri)

    val interpreterUOpt: LogoApp ~> Option = InterpreterOpt or PenInterpreterOpt
    val ruo = programMP(startPosition).foldMap(interpreterUOpt)
    println(ruo)
  }
}
