package monads

/**
  * https://etorreborre.blogspot.com/2011/06/essence-of-iterator-pattern.html
  */
sealed trait BinaryTree[A]
case class Leaf[A](a: A) extends BinaryTree[A]
case class Bin[A](left: BinaryTree[A], right: BinaryTree[A]) extends BinaryTree[A]


