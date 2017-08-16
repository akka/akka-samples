package functionalmodeling.chapter03_designing
package repository
package partial

import scala.language.higherKinds
import scala.language.implicitConversions

object Syntax {

  implicit class FunctorSyntax[F[_]: Functor, A](a: F[A]) {
    def map[B](f: A => B) = Functor[F].map(a)(f)
  }

  implicit class Function1Functor[A1, A](a: Function1[A1, A]) {
    def map[B](f: A => B) = Functor[({type f[x] = Function1[A1, x]})#f].map(a)(f)
  }

  implicit class MonadSyntax[M[_]: Monad, A](a: M[A]) {
    def unit[A](a: => A) = Monad[M].unit(a)

    def flatMap[B](f: A => M[B]): M[B] = Monad[M].flatMap(a)(f)
  }

  implicit class Function1Monad[A1, A](a: Function1[A1, A]) {
    def unit[A](a: => A) = Monad[({type f[x] = Function1[A1, x]})#f].unit(a)

    def flatMap[B](f: A => A1 => B) = Monad[({type f[x] = Function1[A1, x]})#f].flatMap(a)(f)
  }
}
