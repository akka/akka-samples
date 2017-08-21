package functionalmodeling.chapter04_patterns
package patterns
package monoids.foldable

import scala.language.higherKinds

/**
  *
  */
trait Foldable[F[_]] {
  def foldl[A, B](as: F[A], z: B, f: (B, A) => B): B

  def foldMap[A, B](as: F[A])(f: A => B)(implicit m: Monoid[B]): B =
    foldl(as, m.zero, (b: B, a: A) => m.op(b, f(a)))
}

object Foldable {
  implicit val listFoldable = new Foldable[List] {
    override def foldl[A, B](as: List[A], z: B, f: (B, A) => B) =
      as.foldLeft(z)(f)
  }
}

object FoldUtils {
  def mapReduce[F[_], A, B](as: F[A])(f: A => B)
                           (implicit fd: Foldable[F], m: Monoid[B]) =
    fd.foldMap(as)(f)
}
