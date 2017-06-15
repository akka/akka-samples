package typesystem

/**
  * Sequencing Computations
  */
sealed trait LinkedList[A] {

  def length: Int = fold(0)((_, b) => 1 + b)
    /*this match {
    case End() => 0

    case Pair(hd, tl) => 1 + tl.length
  }*/

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

  def fold[B](end: B)(pair: (A, B) => B): B = this match {
    case End() => end
    case Pair(h, t) =>
      //t.fold(f(h, end), f)
      //f(h, t.fold(end, f))
      pair(h, t.fold(end)(pair))
  }

  def map[B](fn: A => B): LinkedList[B] = this match {
    case End() => End()
    case Pair(h, t) =>
      Pair(fn(h), t.map(fn))
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

object LinkedListApp {
  def main(args: Array[String]): Unit = {
    val list: LinkedList[Int] = Pair(1, Pair(2, Pair(3, End())))
    println(list)
    val d = list.map(2 * )
    val addOne = list.map(1 + )
    val divide3 = list.map(_ / 3)
    
    println(d)
    println(addOne)
    println(divide3)
  }
}