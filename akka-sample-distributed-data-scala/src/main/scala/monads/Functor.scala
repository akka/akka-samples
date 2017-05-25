package monads

/**
  * http://www.jianshu.com/p/31377066bf97
  */

trait SemiGroup[A] {
  def op(a1: A, a2: A): A
}

trait Monoid[A] extends SemiGroup[A] {
  def zero: A
}

object Monoid {
  val stringMonoid = new Monoid[String] {
    override def op(a1: String, a2: String): String = a1 + a2

    override def zero = ""
  }

  def listMonoid[A] = new Monoid[List[A]] {
    override def op(a1: List[A], a2: List[A]) = a1 ++ a2

    override def zero = List.empty[A]
  }

  def optionMonoid[A] = new Monoid[Option[A]] {
    override def op(a1: Option[A], a2: Option[A]) = a1 orElse a2

    override def zero = None
  }
}

trait Functor[F[_]] {
  def map[A, B](a: F[A])(f: A => B): F[B] //= fmap(f)(a)

  def fmap[A, B](f: A => B): F[A] => F[B] = map[A, B](_)(f)
}

object Functor {
  def listFunctor[A] = new Functor[List] {
    override def map[A, B](a: List[A])(f: A => B) = a.map(f)
  }
}

trait Monad[M[_]] {
  def unit[A](a: A): M[A]  // Identity

  def join[A](mma: M[M[A]]): M[A] = flatMap(mma)(ma => ma)

  def flatMap[A, B](ma: M[A])(f: A => M[B]): M[B]
}

trait Functor2[F[_]] {
  def fmap[A, B](f: A => B): F[A] => F[B]
}

trait Pointed[F[_]] {
  def point[A](a: => A): F[A]
}

object PointedList extends Pointed[List] {
  override def point[A](a: => A) = List(a)
}

trait PointedFunctor[F[_]] {
  val functor: Functor2[F]
  val pointed: Pointed[F]

  def point[A](a: => A): F[A] = pointed.point(a)

  def fmap[A, B](f: A => B): F[A] => F[B] = functor.fmap(f)
}

trait Applic[F[_]] {
  def applic[A, B](f: F[A => B]): F[A] => F[B]
}

class M[A](value: A) {
  //private def flatten[B](x: M[M[B]]): M[B] = ???
  private def unit[B](value: B): M[B] = new M(value)
  //def map[B](f: A => B): M[B] = ???
  def map[B](f: A => B): M[B] = flatMap { x => unit(f(x)) }
  //def flatMap[B](f: A => M[B]): M[B] = flatten(map(f))
  def flatMap[B](f: A => M[B]): M[B] = ???
}