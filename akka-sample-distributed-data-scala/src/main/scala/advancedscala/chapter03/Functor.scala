package advancedscala.chapter03

import cats.Functor

import scala.language.higherKinds

/**
  * Functor
  */
/*trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
}*/

object Functors {
  implicit val treeFunctor = new Functor[Tree] {
    
    override def map[A, B](fa: Tree[A])(f: A => B): Tree[B] = fa match {
      case Branch(l, r) => Branch(left = map(l)(f), right = map(r)(f))
      case Leaf(v) => Leaf(f(v))
    }
  }
}
