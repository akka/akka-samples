package functionalmodeling.chapter04_patterns
package patterns

import scala.language.higherKinds

/**
  *
  */
trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

object Functor {

  def ListFunctor: Functor[List] = new Functor[List] {
    override def map[A, B](fa: List[A])(f: A => B) = fa.map(f)
  }

  def OptionFunctor: Functor[Option] = new Functor[Option] {
    override def map[A, B](fa: Option[A])(f: A => B) = fa.map(f)
  }
}
