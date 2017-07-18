import cats.Monad

import scala.annotation.tailrec
import scala.language.postfixOps

val optionMonad = new Monad[Option] {
  override def flatMap[A, B](opt: Option[A])(
                            fn: A => Option[B]
  ): Option[B] = opt.flatMap(fn)

  override def pure[A](opt: A): Option[A] = Some(opt)

  @tailrec
  override def tailRecM[A, B](a: A)
                             (fn: A => Option[Either[A, B]]): Option[B] =
    fn(a) match {
      case None => None
      case Some(Left(a1)) => tailRecM(a1)(fn)
      case Some(Right(b)) => Some(b)
    }
}

val two = optionMonad.pure(2)
two.map { 2 * }

sealed trait Tree[+A]
final case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]
final case class Leaf[A](v: A) extends Tree[A]

def branch[A](left: Tree[A], right: Tree[A]): Tree[A] = Branch(left, right)
def leaf[A](value: A): Tree[A] = Leaf(value)

implicit val treeMonad = new Monad[Tree] {
  override def flatMap[A, B](tree: Tree[A])(fn: A => Tree[B]): Tree[B] =
    tree match {
      case Branch(left, right) => branch(flatMap(left)(fn), flatMap(right)(fn))
      case Leaf(v) => fn(v)
    }

  override def pure[A](v: A): Tree[A] = leaf(v)

  override def tailRecM[A, B](a: A)(f: A => Tree[Either[A, B]]): Tree[B] =
    f(a) match {
      case Leaf(Left(a1)) => tailRecM(a1)(f)
      case Leaf(Right(b)) => leaf(b)
      /*case Branch(Leaf(Left(a1)), Leaf(Left(a2))) =>
        branch(tailRecM(a1)(f), tailRecM(a2)(f))
      case Branch(Leaf(Left(a1)), Leaf(Right(b))) =>
        branch(tailRecM(a1)(f), pure(b))
      case Branch(Leaf(Right(b)), Leaf(Left(a2))) =>
        branch(pure(b), tailRecM(a2)(f))
      case Branch(Leaf(Right(b1)), Leaf(Right(b2))) =>
        branch(pure(b1), pure(b2))*/
      case Branch(l, r) =>
        branch(
          flatMap(l) {
            case Left(a1) =>
              tailRecM(a1)(f)
            case Right(b) =>
              pure(b)
          },
          flatMap(r) {
            case Left(a1) =>
              tailRecM(a1)(f)
            case Right(b) =>
              pure(b)
          }
        )

    }
}

import cats.syntax.functor._
import cats.syntax.flatMap._

branch(leaf(100), leaf(200)).flatMap { x =>
  branch(leaf(x - 1), leaf(x + 1))
}

for {
  a <- branch(leaf(100), leaf(200))
  b <- branch(leaf(a - 10), leaf( a + 20))
  //c <- branch(leaf(b - 3), leaf(b + 6))
} yield b

for {
  a <- branch(leaf(100), leaf(200))
  b <- branch(leaf(a - 10), leaf( a + 20))
  c <- branch(leaf(b - 3), leaf(b + 6))
} yield c

