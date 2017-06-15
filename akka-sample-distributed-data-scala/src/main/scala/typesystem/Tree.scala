package typesystem

/**
  * Modelling Data
  */
sealed trait Tree[A] {
  def fold[B](node: (B, B) => B, leaf: A => B): B
}

final case class Node[A](left: Tree[A], right: Tree[A]) extends Tree[A] {
  override def fold[B](node: (B, B) => B, leaf: (A) => B) =
    node(left.fold(node, leaf), right.fold(node, leaf))
}
final case class Leaf[A](v: A) extends Tree[A] {
  override def fold[B](node: (B, B) => B, leaf: (A) => B) = leaf(v)
}

// case class Pair[A, B](one: A, two: B)

object TreeApp {
  def main(args: Array[String]): Unit = {
    val tree: Tree[String] = Node(
      Node(Leaf("To"), Leaf(" iterate")),
      Node(
        Node(Leaf(" is "), Leaf(" human ")),
        Node(
          Leaf(" to "),
          Node(
            Leaf("recurse"), Leaf("divine")
          )
        )

      )
    )

    val result = tree.fold((a: String, b: String) => a + b, a => a)
    println(result)
  }
}