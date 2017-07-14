package advancedscala.chapter04_monad

import scala.language.higherKinds

trait Monad[F[_]] {
  def pure[A](a: A): F[A]

  def flatMap[A, B](value: F[A])(f: A => F[B]): F[B]

  def map[A, B](value: F[A])(f: A => B): F[B] = {
    //pure(f(value))
    flatMap(value)(a => pure(f(a)))
  }
}

