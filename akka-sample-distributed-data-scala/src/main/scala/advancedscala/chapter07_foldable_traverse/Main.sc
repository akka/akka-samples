
def show[A](list: List[A]): String =
  list.foldLeft("nil")((accum, item) => s"$item then $accum")

show(Nil)
show(List("James", "Wade", "Bosh"))

List(1, 5, 7).foldLeft(List.empty[Int])((b, i) => i :: b)
List(1, 5, 7).foldRight(List.empty[Int])( _ :: _)

def listMap[A, B](l: List[A])(f: A => B): List[B] = l.foldRight(List.empty[B]) { (i, acc) =>
  f(i) :: acc
}

def listFlatMap[A, B](l: List[A])(f: A => List[B]): List[B] =
  l.foldRight(List.empty[B]) { (i, acc) =>
    f(i) ::: acc
  }

def listFilter[A](l: List[A])(f: A => Boolean): List[A] =
  l.foldRight(List.empty[A]) { (i, acc) =>
    if (f(i)) i :: acc
    else acc
  }

def listSum(l: List[Int]) = l.foldRight(0)((i, acc) => i + acc)

listMap(List(1, 3, 5))(2 +)
listFlatMap(List(1, 3, 5))(a => List(a * 2))
listFilter(List(1, 3, 5))(_ > 3)
listSum(List(1, 3, 5))

import cats.{Eval, Foldable}
import cats.instances.list._

import scala.concurrent.{Await, Future}

val ints = List(1, 3, 5)
Foldable[List].foldLeft(ints, 0)(_ + _)

import cats.instances.map._
import cats.instances.stream._

type StringMap[A] = Map[String, A]
val stringMap = Map("a" -> "b", "c" -> "d")
Foldable[StringMap].foldLeft(stringMap, "nil")(_ + "!" + _)

def bigdata = (1 to 100000).toStream
//bigdata.foldRight(0)(_ + _)

val eval = Foldable[Stream].foldRight(bigdata, Eval.now(0)) { (num, eval) =>
  eval.map(_ + num)
}
eval.value

import cats.instances.option._

Foldable[Option].nonEmpty(Option(30))
Foldable[Option].nonEmpty(None)

import cats.instances.double._
Foldable[List].combineAll(List(10.5, 90.2, 300))

val hostnames = List(
  "spark.apache.org",
  "www.akka.io",
  "www.playframework.com",
  "www.scala-lang.org"
)

def getUptime(hostname: String): Future[Int] = Future(hostname.length * 60)

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

val allUptimes: Future[List[Int]] = hostnames.foldLeft(
  Future(List.empty[Int])) { (accum, host) =>
    val uptime = getUptime(host)
    for {
      acc <- accum
      ut <- uptime
    } yield acc :+ ut
  }
Await.result(allUptimes, 1 second)

val al2 = Future.traverse(hostnames)(getUptime)
Await.result(al2, 1 second)

import cats.Applicative
import cats.instances.future._
import cats.syntax.applicative._

List.empty[Int].pure[Future]

def oldCombine(
              accum: Future[List[Int]],
              host: String
              ): Future[List[Int]] = {
  val t = getUptime(host)
  for {
    acc <- accum
    ut <- t
  } yield ut :: acc

}