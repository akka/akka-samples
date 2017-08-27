package fpinscala.chapter02_gettingstarted

import scala.annotation.tailrec

/**
  * Created by liaoshifu on 2017/8/27
  */
object Exercise {

  // Exercise 2.1
  def fib(n: Int): Int = {
    @tailrec
    def go(prev: Int, current: Int, n: Int): Int = {
      if (n == 0) 0
      else if (n <= 1) current
      else go(current, prev + current, n - 1)
    }

    go(0, 1, n)
  }

  // Exercise 2.2
  def isSorted[A](as: Array[A], ordered: (A, A) => Boolean): Boolean = {

    @tailrec
    def go(i: Int, prev: A): Boolean = {
      if (i == as.length) true
      else if (ordered(prev, as(i))) go(i + 1, as(i))
      else false
    }

    if (as.length == 0) true
    go(1, as(0))
  }

  // Exercise 2.3
  def curry[A, B, C](f: (A, B) => C): A => B => C = a => b => f(a, b)

  // Exercise 2.4
  def uncurry[A, B, C](f: A => B => C): (A, B) => C = (a, b) => f(a)(b)

  // Exercise 2.5
  def compose[A, B, C](f: B => C, g: A => B): A => C = a => f(g(a))

  def main(args: Array[String]): Unit = {

    val f10 = fib(10)
    println(f10)
    
    val as1 = Array(1, 3, 5, 8, 2)
    val as2 = Array(2, 3, 5, 7)
    val as3 = Array(9, 7, 5, 3, 1)
    val ordered = (a1: Int, a2: Int) => a1 < a2
    println(isSorted(as1, ordered))
    println(isSorted(as2, ordered))
    println(isSorted(as3, ordered))

    val addInt: (Int, Int) => Int = (a1, a2) => a1 + a2

    val addIntC: Int => Int => Int = x => y => x + y

    val r = curry(addInt)
    println(r(3)(4))

    val rc = uncurry(addIntC)
    println(rc(3, 4))
  }

}
