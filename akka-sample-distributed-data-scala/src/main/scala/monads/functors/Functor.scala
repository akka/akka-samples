package monads.functors

/**
  * https://medium.com/@agaro1121/functors-1d675d314c1d
  */
trait Functor[F[_]] {
  def map[A, B](fa: F[A])(f: A => B): F[B]
  //def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]

  //def unit[A](a: A): F[A]
}

object Functor {
  case class FunctorOps[F[_], A](fa: F[A]) {
    def map[B](f: A => B)(implicit functor: Functor[F]): F[B] =
      functor.map(fa)(f)
  }

  implicit def toFunctorOps[F[_], A](fa: F[A]) = FunctorOps(fa)
}

sealed trait Tree[A]
case class Leaf[A](value: A) extends Tree[A]
case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]

object Tree {

  implicit val treeFunctor = new Functor[Tree] {
    override def map[A, B](tree: Tree[A])(f: A => B): Tree[B] = tree match {
      case Leaf(a) => Leaf(f(a))

      case Branch(left, right) =>  Branch(map(left)(f), map(right)(f))

    }

    /*override def flatMap[A, B](fa: Tree[A])(f: (A) => Tree[B]) = fa match {
      case Leaf(a) => f(a)

      case Branch(left, right) => Branch()
    }

    override def unit[A](a: A): Tree[A] = Leaf(a)*/
  }

  def branch[A](left: Tree[A], right: Tree[A]): Tree[A] = Branch(left, right)

  def leaf[A](a: A): Tree[A] = Leaf(a)
}

/*object TreeFunctor extends Functor[Tree] {
  override def map[A, B](tree: Tree[A])(f: A => B): Tree[B] = tree match {
    case Leaf(a) => Leaf(f(a))

    case Branch(left, right) =>  Branch(map(left)(f), map(right)(f))

  }
}*/

object FunctorTester extends App {

  import Functor._
  import Tree._

  val tree: Tree[Int] = Branch(
    Branch(Leaf(4), Branch(Leaf(5), Leaf(6))),
    Leaf(8)
  )

  val tree2 = branch(
    branch(leaf(4), branch(leaf(5), leaf(6))),
    leaf(8)
  )
  //val mappedTree = TreeFunctor.map(tree)(_ + 2)

  //println(mappedTree)
  println(tree.map(_ + 2))
  println(tree2.map(_ + 2))
  println(tree.map(n => s"n = $n"))
}