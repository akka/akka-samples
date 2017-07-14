import cats.data.Writer
import cats.instances.vector._

Writer(Vector(
  "It was the best of times",
  "It was the worst of times"
), 123)

import cats.syntax.applicative._

type Logged[A] = Writer[Vector[String], A]

val p = 123.pure[Logged]

p.value

import cats.syntax.writer._
Vector("msg1", "msg2", "msg3").tell

val b = 123.writer(Vector("msg1", "msg2", "msg3"))
b.written
b.value
val (log, result) = b.run

val writer1 = for {
  a <- 10.pure[Logged]
  _ <- Vector("a", "b", "c").tell
  b <- 32.writer(Vector("x", "y", "z"))
} yield a + b

writer1.run

val writer2 = writer1.mapWritten(_.map(_.toUpperCase))

val writer3 = writer1.bimap(
  log => log.map(_.toUpperCase),
  result => result * 10
)

val writer4 = writer1.mapBoth { (log, result) =>
  val log2 = log.map(_ + "!")
  val result2 = result * 20
  (log2, result2)
}

val writer5 = writer1.reset

val writer6 = writer1.swap

writer6.value
writer6.written

def slow[A](body: => A) = try body finally Thread.sleep(100)

def factorial(n: Int): Int = {
  val ans = slow(if (n == 0) 1 else n * factorial(n - 1))
  println(s"fact $n $ans")
  ans
}

factorial(5)

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

Await.result(Future.sequence(Vector(
  Future(factorial(5)),
  Future(factorial(5))
)), 5.seconds)

def factorialWriter(n: Int): Logged[Int] = {
  //val ans = slow(if (n == 0) 1 else factorialWriter(n - 1).map(n * ).value)
  //ans.writer(s"fact $n $ans")
  val ans = slow(if (n == 0) 1.pure[Logged] else factorialWriter(n - 1)
    .mapBoth((log, result) =>
      (log :+ s" fact $n $result", result * n)
  ))
  ans
}

val (logw, resultw) = factorialWriter(5).run

Await.result(Future.sequence(Vector(
  Future(factorialWriter(5)),
  Future(factorialWriter(5))
)), 5.seconds)

def factorialAnswer(n: Int): Logged[Int] = for {
  ans <- if (n == 0) {
    1.pure[Logged]
  } else slow(factorialAnswer(n - 1).map(n * ))
  _ <- Vector(s"fact $n $ans").tell
} yield ans

val (log1, result1) = factorialAnswer(5).run

val Vector((logA, ansA), (logB, ansB)) = Await.result(
  Future.sequence(Vector(
  Future(factorialAnswer(5).run),
  Future(factorialAnswer(5).run)
)), 5.seconds)
