import cats.Monad
import cats.instances.option._
import cats.instances.list._

val opt1 = Monad[Option].pure(3)
val opt2 = Monad[Option].flatMap(opt1)(a => Some(a + 2))
val opt3 = Monad[Option].map(opt2)(a => 100 * a)

val list1 = Monad[List].pure(30)
val list2 = Monad[List].flatMap(List(1, 3, 5))(x => List(x, x * 2))
val list3 = Monad[List].map(list2)(_ + 123)

import cats.instances.future._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

val fm = Monad[Future]

Await.result(fm.flatMap(fm.pure(20)) { x =>
    fm.pure(x + 5)
  },
  1.second
)

import cats.syntax.applicative._
1.pure[Option]
2.pure[List]

import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.option._

def sumSquare[M[_]: Monad](a: M[Int], b: M[Int]): M[Int] =
  a.flatMap(x => b.map(y => x * x + y * y))

sumSquare(Option(3), Option(5))
sumSquare(List(1, 2, 3), List(4, 5, 6, 8))
//val n: Option[Int] = None
sumSquare(Option(2), Option.empty[Int])
sumSquare(List(2, 4, 6), List.empty[Int])

import cats.Id
sumSquare(3: Id[Int], 6: Id[Int])

val a = Monad[Id].pure(3)
val b = Monad[Id].flatMap(a)(1 + )

Await.result(sumSquare(Future(3), Future(4)), 1.second)
sumSquare(a, b)

def pure[A](value: A): Id[A] = value

def map[A, B](initial: Id[A])(func: A => B): Id[B] = func(initial)

def flatMap[A, B](initial: Id[A])(f: A => Id[B]): Id[B] = f(initial)

pure(123)

map(123)(2 * )
flatMap(123)(2 *)

import cats.syntax.either._
for {
  a <- 1.asRight[String]
  b <- 0.asRight[String]
  c <- if (b == 0) "DIVO".asLeft[Int] else (a / b).asRight[String]
} yield c * 100

sealed trait LoginError extends Product with Serializable
final case class UserNotFound(username: String) extends LoginError
final case class PasswordIncorrect(username: String) extends LoginError
case object UnexpectedError extends LoginError
case class User(username: String, password: String)

type LoginResult = Either[LoginError, User]

def handleError(error: LoginError): Unit = error match {
  case UserNotFound(u) =>
    println(s"User not found: $u")
  case PasswordIncorrect(u) =>
    println(s"Password incorrect: $u")
  case UnexpectedError =>
    println(s"Unexpected error")
}

val result1: LoginResult = User("dave", "passw0rd").asRight
val result2: LoginResult = UserNotFound("james").asLeft
result1.fold(handleError, println)
result2.fold(handleError, println)

import cats.Eval

val now = Eval.now {
  println("Computing now")
  1 + 2
}
val later = Eval.later {
  println("Computing later")
  1 + 3
}
val always = Eval.always {
  println("Computing always")
  1 + 4
}

now.value
now.value
later.value
later.value
always.value
always.value

val greeting = Eval.always {
  println("Step 1")
  "hello"
}.map { str =>
  println("Step 2")
  str + "world"
}

greeting.value

val ans = for {
  a <- Eval.now {
    println("Calculating A")
    40
  }
  b <- Eval.always {
    println("Calculating B")
    2
  }
} yield {
  println("Adding A and B")
  a + b
}

ans.value
ans.value

def factorial(n: BigInt): BigInt = {
  if (n == 1 ) n else factorial(n - 1) * n
}

factorial(500)

def factorialEval(n: BigInt): Eval[BigInt] = {
  if (n == 1) Eval.now(n)
  else Eval.defer(factorialEval(n - 1).map(_ * n))
}

factorialEval(50000).value

def foldRight[A, B](as: List[A], acc: B)(fn: (A, B) => B): B = as match {
  case h :: t =>
    fn(h, foldRight(t, acc)(fn))
  case Nil =>
    acc
}

def foldRightEval[A, B](as: List[A], acc: Eval[B])(fn: (A, Eval[B]) => Eval[B]): Eval[B] = as match {
  case h :: t =>
    Eval.defer(fn(h, foldRightEval(t, acc)(fn)))
  case Nil =>
    acc
}

val fs = foldRightEval(List(1, 3, 5, 7), Eval.now(30))((a, eb) => eb.map(_ + a))
fs.value

val fl = foldRightEval((1L to 100000L).toList, Eval.now(0L))(
  (a, eb) => eb.map(_ + a))

fl.value

def foldRightUsingEval[A, B](as: List[A], acc: B)(fn: (A, B) => B): B =
  foldRightEval(as, Eval.now(acc))((a, eb) => eb.map(fn(a, _))).value

val fr = foldRightUsingEval((1L to 100000L).toList, 0L)((a, b) => a + b)
fr
