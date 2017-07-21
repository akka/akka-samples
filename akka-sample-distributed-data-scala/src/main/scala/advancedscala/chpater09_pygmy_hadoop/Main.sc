import cats.Monoid
import cats.instances.vector._
import cats.syntax.monoid._

def foldMap[A, B: Monoid](values: Vector[A])(f: A => B): B = {
 // values.map(f).foldLeft(Monoid[B].empty)(_ |+| _)
  values.foldLeft(Monoid[B].empty)((b, a) => b |+| f(a))
}

import cats.instances.int._
foldMap(Vector(1, 3, 5))(identity)
import cats.instances.string._
foldMap(Vector("James", "Wade", "Bosh"))(_ + "!!!")
foldMap(Vector("bigdata", "ai", "clouod"))(_.toUpperCase)

