import cats.Monoid
import cats.instances.all._
import cats.syntax.semigroup._

Option(1) |+| None

val map1 = Map("a" -> 1, "b" -> 2, "d" -> 4)
val map2 = Map("b" -> 3, "c" -> 9, "d" -> 5)
map1 |+| map2

val tuple1 = ("hello", 1234)
val tuple2 = ("yarn", 40)
tuple1 |+| tuple2

List(3, 5) |+| List(4, 8)
