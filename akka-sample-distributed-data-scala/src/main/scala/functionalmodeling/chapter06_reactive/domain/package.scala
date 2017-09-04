package functionalmodeling.chapter06_reactive

import scalaz._
import scala.concurrent._
import ExecutionContext.Implicits.global

package object domain {
  type Valid[A] = EitherT[Future, NonEmptyList[String],  A]

  def addEitherTFuture[A](a: => NonEmptyList[String] \/ A): Valid[A] = EitherT { Future { a } }
}
