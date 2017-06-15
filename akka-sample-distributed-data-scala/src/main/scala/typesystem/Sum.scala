package typesystem

/**
  * Variance
  */
sealed trait Sum[+A, +B] {
  def flatMap[AA >: A, C](f: B => Sum[AA, C]): Sum[AA, C] = this match {
    case Left(v) => Left(v)
    case Right(v) => f(v)
  }

  def fold[C](error: A => C, success: B => C): C = this match {
    case Left(v) => error(v)
    case Right(v) => success(v)
  }

  def map[C](f: B => C): Sum[A, C] = this match {
    case Left(v) => Left(v)
    case Right(v) => Right(f(v))
  }
}

final case class Left[A](v: A) extends Sum[A, Nothing]
final case class Right[B](v: B) extends Sum[Nothing, B]
