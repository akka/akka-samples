package typesystem

sealed trait Maybe[+A] {
  def map[B](f: A => B): Maybe[B] = this match {
    case Full(v) => Full(f(v))
    case Empty => Empty
  }

  def map2[B](f: A => B): Maybe[B] = flatMap(a => Full(f(a)))

  def flatMap[B](f: A => Maybe[B]): Maybe[B] = this match {
    case Full(v) => f(v)
    case Empty => Empty
  }
}
final case class Full[A](v: A) extends Maybe[A]
final case object Empty extends Maybe[Nothing]

object MaybeApp {
  def main(args: Array[String]): Unit = {
    val possible: Maybe[Int] = Empty
  }
}