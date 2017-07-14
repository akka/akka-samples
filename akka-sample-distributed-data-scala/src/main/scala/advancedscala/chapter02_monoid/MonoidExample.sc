//package advancedscala.chapter02

import cats.Monoid
import cats.instances.string._
import cats.instances.int._
import cats.instances.long._
import cats.instances.option._
import cats.instances.set._


Monoid[String].combine("Hi", "James")
//val si = Monoid.combine("O", 0)
//println(si)

Monoid[Set[Int]].combine(Set(30, 50), Set(9, 2))
Monoid[Option[String]].combine(Option("Jame"), None)

import cats.syntax.monoid._
val stringResult = "Hi" |+| "James"

import cats.instances.list._
//def add(items: List[Int]): Int = items.sum
def add[A](items: List[A])(implicit monoid: cats.Monoid[A]) =
  items.foldLeft(monoid.empty)(_ |+| _)
//add(List(1, 3, 5, 7, 9))

def add2(items: List[Int]): Int =
  items.foldLeft(Monoid[Int].empty)(_ |+| _)
add2(List(1, 3, 5, 7, 9))

add(List(1, 3, 5, 7, 9))

add(List(List(1, 5), List( 2, 4), List(3, 6)))

add(List(Some(1), None, Some(5), Some(7), Some(9)))

case class Order(totalCost: Double, quantity: Double)

object Order {
  implicit object OrderMonoid extends Monoid[Order] {
    override def empty = Order(0.0, 0.0)

    override def combine(x: Order, y: Order) = Order(
      x.totalCost + y.totalCost, x.quantity + y.quantity
    )
  }
}
import cats.syntax.option._

add(List(1.some, 2.some, 5.some, none[Int]))

add(List(Order(2.0, 3.0), Order(5.2, 8.8)))


