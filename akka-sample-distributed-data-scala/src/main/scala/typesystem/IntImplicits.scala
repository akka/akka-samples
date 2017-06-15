package typesystem

/**
  * typeclass
  */
object IntImplicits {

  /*trait Yeah[A] {
    def yeah(i: A): Unit
    def time(f: A => Unit): A => Unit
  }

  implicit class YeahOps[A](val i: A) extends AnyVal {
    def yeah(implicit y: Yeah[A]) = y.yeah(i)
    def time(f: A => Unit)(implicit y: Yeah[A]): Unit = y.time(f)(i)
  }

  implicit object IntYeah extends Yeah[Int] {
    override def yeah(i: Int) = {
      val f = (_: Int) => println("Oh, yeah!")
      time(f)(i)
    }
    override def time(f: Int => Unit) = (i: Int) => { (0 until i) foreach (ii => f(ii)) }
  }
  */
  implicit class IntOps(i: Int) {
    def yeah = time((_: Int) => println("Oh yeah!"))
    //(1 to i) foreach (_ => println("Oh yeah!"))
    def time(f: Int => Unit) = (0 until i) foreach (ii => f(ii))
  }
}

object ImplicitsApp {
  def main(args: Array[String]): Unit = {
    import IntImplicits._
    1.yeah
    2.yeah
    //-1.yeah
    3.time(i => println(s"Look - it's the number $i!"))
  }
}
