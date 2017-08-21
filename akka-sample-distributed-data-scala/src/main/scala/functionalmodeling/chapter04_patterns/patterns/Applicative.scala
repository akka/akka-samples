package functionalmodeling.chapter04_patterns.patterns

import scala.language.higherKinds

/**
  *
  */
trait Applicative[F[_]] extends Functor[F] {
  def ap[A, B](fa: => F[A])(f: => F[A => B]): F[B]

  def apply2[A, B, C](fa: F[A], fb: F[B])(f: (A, B) => C): F[C] = {
    val fab = map(fa)(f.curried)
    ap(fb)(fab)
  }

  def lift2[A, B, C](f: (A, B) => C): (F[A], F[B]) => F[C] = (fa: F[A], fb: F[B]) =>
    apply2(fa, fb)(f)
    //apply2(_, _)(f)

  def unit[A](a: A): F[A]
}

object Applicative {
  def listApply: Applicative[List] = new Applicative[List] {
    override def ap[A, B](fa: =>List[A])(f: =>List[A => B]) = for {
      a <- fa
      ff <- f
    } yield ff(a)

    override def unit[A](a: A) = List(a)

    override def map[A, B](fa: List[A])(f: A => B) = fa map f
  }

  def optionApply: Applicative[Option] = new Applicative[Option] {
    override def ap[A, B](fa: =>Option[A])(f: =>Option[(A) => B]) = for {
      a <- fa
      ff <- f
    } yield ff(a)

    override def unit[A](a: A) = Some(a)

    override def map[A, B](fa: Option[A])(f: (A) => B) = fa map f
  }
}
