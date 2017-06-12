package typesystem

/**
  * Sequencing Computations
  */
sealed trait LinkedList[A] {

  def length: Int = this match {
    case End() => 0

    case Pair(hd, tl) => 1 + tl.length
  }

  def contains(item: A): Boolean = this match {
    case End() => false
    case Pair(h, t) =>
      if (item == h) true
      else t.contains(item)
  }

  def apply(i: Int): Result[A] = this match {
    case End() => Failure("Index out of bounds")
    case Pair(h, t) =>
      if (i == 0) Success(h)
      else t.apply(i - 1)
  }
  /*
  def double: LinkedList = this match {
    case End => End

    case Pair(hd, tl) => Pair(hd * 2, tl.double)
  }

  def product: Int = this match {
    case End => 1
    case Pair(h, t) => h * t.product
  }

  def sum: Int = this match {
    case End => 0
    case Pair(h, t) => h + t.sum
  }*/
}

final case class End[A]() extends LinkedList[A]
final case class Pair[A](head: A, tail: LinkedList[A]) extends LinkedList[A]

