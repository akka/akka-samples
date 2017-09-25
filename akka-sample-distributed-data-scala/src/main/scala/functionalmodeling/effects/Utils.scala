package functionalmodeling.effects

import scala.language.higherKinds

import cats.{Foldable, Monoid}

/**
  *
  */
trait Utils {
  def mapReduce[F[_], A, B](as: F[A])(f: A => B)
                           (implicit fd: Foldable[F], m: Monoid[B]): B = fd.foldMap(as)(f)
}
