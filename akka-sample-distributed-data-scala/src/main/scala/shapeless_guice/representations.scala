package shapeless_guice

final case class Employee(
                         name: String,
                         number: Int,
                         manager: Boolean
                         )

final case class Employee2(name: String, age: Int, manager: Boolean, depth: Option[String])

final case class IceCream(
                           name: String,
                           numCherries: Int,
                           inCone: Boolean
                         )

final case class Person(
                       name: String,
                       age: Int,
                       address: List[String]
                       )

sealed trait Shape
final case class Rectangle(width: Double, height: Double) extends Shape
final case class Triangle(one: Double, two: Double, three: Double) extends Shape
final case class Circle(radius: Double) extends Shape

sealed trait Tree[A]
case class Branch[A](left: Tree[A], right: Tree[A]) extends Tree[A]
case class Leaf[A](value: A) extends Tree[A]

case class Wrapper(value: Int)

case class IceCreamV1(name: String, numCherries: Int, inCone: Boolean)
case class IceCreamV2a(name: String, inCone: Boolean)
case class IceCreamV2b(name: String, inCone: Boolean, numCherries: Int)
case class IceCreamV2C(name: String, inCone: Boolean, numCherries: Int, numWaffles: Int)
