package cats_tut

import simulacrum._

/**
  * http://eed3si9n.com/herding-cats/
  */
/*@typeclass trait CanAppend[A] {
  @op("|+|") def append(a1: A, a2: A): A
}*/

/*object HerdingCatsApp {

  def main(args: Array[String]): Unit = {

    implicit val intCanAppend: CanAppend[Int] = new CanAppend[Int] {
      override def append(a1: Int, a2: Int): Int = a1 + a2
    }

    import CanAppend.ops._

    println(1 |+| 2)
  }
}*/
