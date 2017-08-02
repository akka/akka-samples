import cats.Monoid
import cats.instances.vector._
import cats.syntax.monoid._
import cats.instances.future._
import cats.syntax.traverse._

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

def foldMap[A, B: Monoid](values: Vector[A])(f: A => B): B = {
 // values.map(f).foldLeft(Monoid[B].empty)(_ |+| _)
  values.foldLeft(Monoid[B].empty)((b, a) => b |+| f(a))
}

import cats.instances.int._
foldMap(Vector(1, 3, 5))(identity)
import cats.instances.string._
foldMap(Vector("James", "Wade", "Bosh"))(_ + "!!!")
foldMap(Vector("bigdata", "ai", "clouod"))(_.toUpperCase)

def parallelFoldMap[A, B: Monoid](value: Vector[A])(f: A => B): Future[B] = {
  val numCores = Runtime.getRuntime.availableProcessors()
  val groupSize = (1.0 * value.size / numCores).ceil.toInt
  val fs = value.grouped(groupSize).map(x => Future{ foldMap(x)(f) }).toVector
  val fr = fs.sequence.map { i =>
    i.foldLeft(Monoid[B].empty)(_ |+| _)
  }
  fr
}

import scala.concurrent.duration._
Await.result(parallelFoldMap((1 to 1000000).toVector)(identity), 2.seconds)

import cats.Foldable
import cats.instances.future._
import cats.Traverse
import cats.syntax.foldable._

def parallelFoldMapUsingCats[A, B: Monoid](value: Vector[A])(f: A => B): Future[B] = {
  val numCores = Runtime.getRuntime.availableProcessors()
  val groupSize = (1.0 * value.size / numCores).ceil.toInt
  value.grouped(groupSize).toVector
    .traverse( t =>
      Future { t.foldMap(f) }
    ).map(_.combineAll)
}

Await.result(parallelFoldMapUsingCats((1 to 1000000).toVector)(identity), 2.seconds)
