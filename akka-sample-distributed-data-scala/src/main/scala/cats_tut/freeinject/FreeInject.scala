package cats_tut.freeinject

import cats._
import cats.data._
import cats.implicits._

import cats.free._

/**
  * https://underscore.io/blog/posts/2017/03/29/free-inject.html
  */

sealed trait Action[A]  //parameter on return type
final case class ReadData(port: Int) extends Action[String]
final case class TransformData(data: String) extends Action[String]
final case class WriteData(port: Int, data: String) extends Action[Unit]

object Action {
  type FreeAction[A] = Free[Action, A]

  def readData(port: Int): FreeAction[String] = Free.liftF(ReadData(port))
  def transformData(data: String): FreeAction[String] = Free.liftF(TransformData(data))
  def writeData(port: Int, data: String): FreeAction[Unit] = Free.liftF(WriteData(port, data))

  val program = for {
    d <- readData(123)
    t <- transformData(d)
    _ <- writeData(789, t)
  } yield ()

  type ActionOrAdvanced[A] = Coproduct[Action, AdvancedAction, A]

  class ActionFree[C[_]](implicit inject: Inject[Action, C]) {
    def readData(port: Int): Free[C, String] = Free.inject[Action, C](ReadData(port))
    def transformData(data: String): Free[C, String] = Free.inject[Action, C](TransformData(data))
    def writeData(port: Int, data: String): Free[C, Unit] = Free.inject[Action, C](WriteData(port, data))
  }
  
}
