package monads.churchstate

/**
  * https://underscore.io/blog/posts/2017/06/02/uniting-church-and-state.html
  */
sealed trait Result[+A]
final case class Emit[A](get: A) extends Result[A]
final case object Waiting extends Result[Nothing]
final case object Complete extends Result[Nothing]
final case class Error(reason: Throwable) extends Result[Nothing]
