package functionalmodeling.chapter04_patterns.patterns

import scala.language.higherKinds

/**
  *
  */
trait Monad[F[_]] extends Functor[F] {

  def unit[A](a: => A): F[A]

  def flatMap[A, B](ma: F[A])(f: A => F[B]): F[B] =
    join(map(ma)(f))

  def join[A](mma: F[F[A]]): F[A] = flatMap(mma)(ma => ma)

  def map[A, B](ma: F[A])(f: A => B): F[B] =
    flatMap(ma)(a => unit(f(a)))

  def map2[A, B, C](ma: F[A], mb: F[B])(f: (A, B) => C): F[C] = {
    println(s"+++ monad $ma $mb")
    flatMap(ma)(a => map(mb)(b => f(a, b)))
  }

  def sequence[A](lma: List[F[A]]): F[List[A]] =
    lma.foldRight(unit(List[A]())) { (ma, mb) =>
      println(s"from monad $ma")
      map2(ma, mb)(_ :: _)
    }

  def traverse[A,B](la: List[A])(f: A => F[B]): F[List[B]] =
    la.foldRight(unit(List[B]()))((a, mlb) => map2(f(a), mlb)(_ :: _))
  
}

object Monad {
  def apply[F[_]: Monad]: Monad[F] = implicitly[Monad[F]]

  implicit val optionMonad = new Monad[Option] {
    override def unit[A](a: => A) = Some(a)

    override def flatMap[A, B](ma: Option[A])(f: (A) => Option[B]): Option[B] = ma flatMap f
  }

  implicit val listMonad = new Monad[List] {
    override def unit[A](a: => A) = List(a)

    override def flatMap[A, B](ma: List[A])(f: (A) => List[B]): List[B] = ma flatMap f
  }
}