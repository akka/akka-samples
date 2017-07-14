package advancedscala.chapter02_monoid

/**
  * Super Adder
  */
object SuperAdder {

  import cats.syntax.semigroup._

  def add[A](items: List[A])(implicit monoid: cats.Monoid[A]) =
    items.foldLeft(monoid.empty)(_ |+| _)

  def addC[A: cats.Monoid](items: List[A]): A =
    items.foldLeft(cats.Monoid[A].empty)(_ |+| _)

  def add(items: List[Int]): Int = items.foldLeft(0)(_ + _)
}

case class Order(totalCost: Double, quantity: Double)

object Order {
  implicit object OrderMonoid extends Monoid[Order] {
    override def empty = Order(0.0, 0.0)

    override def combine(x: Order, y: Order) = Order(
      x.totalCost + y.totalCost, x.quantity + y.quantity
    )
  }
}