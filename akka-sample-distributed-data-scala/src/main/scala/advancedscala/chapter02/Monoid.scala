package advancedscala.chapter02

trait Monoid[A] extends Semigroup[A] {
  //def combine(x: A, y: A): A
  def empty: A
}

object Monoid {
  def associativeLaw[A](x: A, y: A, z: A)(implicit m: Monoid[A]): Boolean = {
    m.combine(m.combine(x, y), z) == m.combine(x, m.combine(y, z))
  }

  def identityLaw[A](x: A)(implicit m: Monoid[A]): Boolean = {
    m.combine(x, m.empty) == x &&
    m.combine(m.empty, x) == x
  }

  def apply[A](implicit m: Monoid[A]) = m

  object BooleanAndMonoid extends Monoid[Boolean] {
    override def combine(x: Boolean, y: Boolean) = x && y

    override def empty = true
  }

  implicit val booleanOrMonoid: Monoid[Boolean] = new Monoid[Boolean] {
    override def empty = false

    override def combine(x: Boolean, y: Boolean) = x || y
  }

  implicit val booleanEitherMonoid: Monoid[Boolean] = new Monoid[Boolean] {
    override def empty = false

    override def combine(x: Boolean, y: Boolean) = (x && !y) || (!x && y)
  }

  implicit val booleanXnorMonoid: Monoid[Boolean] = new Monoid[Boolean] {
    override def empty = true

    override def combine(x: Boolean, y: Boolean) = (!x || y) && (x || !y)
  }

  implicit def setUnionMonoid[A]: Monoid[Set[A]] = new Monoid[Set[A]] {
    override def empty = Set.empty[A]

    override def combine(x: Set[A], y: Set[A]) = x ++ y
  }

  /*implicit def optionUnionMonoid[A]: Monoid[Option[A]] = new Monoid[Option[A]] {
    override def empty = None

    override def combine(x: Option[A], y: Option[A]) = x ++ y
  }*/

}

trait Semigroup[A] {
  def combine(x: A, y: A): A
}

object MonoidApp {
  def main(args: Array[String]): Unit = {
    val intSetMonoid = Monoid[Set[Int]]
    val r1 = intSetMonoid.combine(Set(1, 3), Set(2, 3, 4))
    println(r1)

    import cats.instances.option._
    import cats.instances.int._
    val intOptionMonoid = cats.Monoid[Option[Int]]
    val o1 = Option(30)
    val o2 = Option(50)
    //val o3 = Option("James")
    val r2 = intOptionMonoid.combine(o1, o2)
    println(r2)
  }
}