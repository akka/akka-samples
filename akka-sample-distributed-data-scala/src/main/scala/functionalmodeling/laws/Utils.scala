package functionalmodeling.laws

import cats._
import cats.implicits._


trait Utils {

  def mapReduce1[F[_], A, B](as: F[A])(f: A => B)
                           (implicit fd: Foldable[F], m: Monoid[B]): B = fd.foldMap(as)(f)

  def mapReduce[F[_], A, B](as: F[A])(f: A => B)
                           (implicit fd: Foldable[F], m: Monoid[B]): B =
    fd.foldLeft(as, m.empty)((b, a) => m.combine(b, f(a)))
}
