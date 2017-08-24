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

  def apply[F[_]: Functor]: Functor[F] = implicitly[Functor[F]]

  implicit def ListFunctor: Functor[List] = new Functor[List] {
    override def map[A, B](fa: List[A])(f: A => B) = fa.map(f)
  }

  implicit def OptionFunctor: Functor[Option] = new Functor[Option] {
    override def map[A, B](fa: Option[A])(f: A => B) = fa.map(f)
  }

  implicit def Tuple2Functor[A1]: Functor[({type f[x] = (A1, x)})#f] = new Functor[({type f[x] = (A1, x)})#f] {
    override def map[A, B](fa: (A1, A))(f: (A) => B) = (fa._1, f(fa._2))
  }
}

object FunctorApp {
  def main(args: Array[String]): Unit = {
    import Functor._

    val x = List(1,2,3,4)
    val f: Int => Int = _ + 1

    println(Functor[List].map(x)(f)) // List(2,3,4,5)

    val l = List(("a", 10), ("b", 20))
    println(Functor[List].map(l)(t => Functor[({type f[x] = (String, x)})#f].map(t)(f)))

    type Tup[A] = (String, A)
    println(l.map(e => Functor[Tup].map(e)(f)))
  }
}

