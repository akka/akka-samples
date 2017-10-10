package monads.churchstate.church

/**
  *
  */
sealed trait Stream[A] {
  import Stream._

  def zip[B](that: Stream[B]): Stream[(A, B)] = Zip(this, that)

  def map[B](f: A => B): Stream[B] = Map(this, f)

  def foldLeft[B](zero: B)(f: (A, B) => B): B = {
    val observable = Observable.fromStream(this)
    observable.foldLeft(zero)(f)
  }
}

object Stream {
  def fromIterator[A](iterator: Iterator[A]): Stream[A] =
    FromIterator(iterator)

  def always[A](elem: A): Stream[A] = fromIterator(Iterator.continually(elem))

  def apply[A](elems: A*): Stream[A] = fromIterator(Iterator(elems: _*))

  case class FromIterator[A](iterator: Iterator[A]) extends Stream[A]
  case class Zip[A, B](left: Stream[A], right: Stream[B]) extends Stream[(A, B)]
  case class Map[A, B](source: Stream[A], f: A => B) extends Stream[B]
}
