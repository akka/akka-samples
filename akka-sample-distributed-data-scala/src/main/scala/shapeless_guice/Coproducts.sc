
import shapeless._
import shapeless.ops.{coproduct, hlist, nat}
import shapeless.ops.hlist.Last

case class Red()
case class Amber()
case class Green()

type Light = Red :+: Amber :+: Green :+: CNil

val red: Light = Inl(Red())

val amber: Light = Inr(Inl(Amber()))

val green: Light = Inr(Inr(Inl(Green())))

green.isInstanceOf[Light]

//val green2: Light = green

println(green)
println(Red())

sealed trait Shape
final case class Rectangle(width: Double, height: Double) extends Shape
final case class Triangle(one: Double, two: Double, three: Double) extends Shape
final case class Circle(radius: Double) extends Shape


val gen = Generic[Shape]
val rect1 = gen.to(Rectangle(3.0, 4.0))
val tri1 = gen.to(Triangle(1.0, 2.0, 3.0))

import shapeless_guice.csv._
import CsvEncoder._
import CsvApp._

CsvEncoder.writeCsv(employees)

case class Vec(x: Int, y: Int)
case class Rect(origin: Vec, size: Vec)

def getRepr[A](value: A)(implicit gen: Generic[A]) = gen.to(value)

getRepr(Vec(1, 2))
getRepr(Rect(Vec(0, 0), Vec(5, 5)))

val last1 = Last[String :: Int :: HNil]
val last2 = Last[Int :: String :: HNil]

last1("James" :: 33 :: HNil)
last2(35 :: "Wade" :: HNil)
//Last[HNil]

import shapeless_guice._
import Second._

val second1 = Second[String :: Boolean :: Int :: HNil]
val second2 = Second[String :: Int :: Boolean :: HNil]

second1("foo" :: true :: 123 :: HNil)
second2("bar" :: 321 :: false :: HNil)

import shapeless.syntax.singleton._

//var x = 42.narrow
val number = 42
trait Cherries

//val numCherries = number.asInstanceOf[Int with Cherries]

import shapeless.labelled._
val someNumber = 123
val numCherries = "numCherries" ->> someNumber

field[Cherries](123)

def getFieldName[K, V](value: FieldType[K, V])
                      (implicit witness: Witness.Aux[K]) = witness.value

getFieldName(numCherries)

def getFieldValue[K, V](value: FieldType[K, V]): V = value
getFieldValue(numCherries)

val garfield = ("cat" ->> "Garfield") :: ("orange" ->> true) :: HNil

("Hello" :: 123 :: true :: HNil).select

("Hello" :: 123 :: true :: HNil).last
("Hello" :: 123 :: true :: HNil).init

type Zero = Nat._0
type One = Succ[Zero]

import shapeless.ops.nat.ToInt
val toInt = ToInt[Nat._22]
toInt()
Nat.toInt[One]

val hlistLength = hlist.Length[String :: Int :: String :: Boolean :: HNil]
Nat.toInt[hlistLength.Out]

val coproductLength = coproduct.Length[Double :+: Char :+: String :+: CNil]
//Nat.toInt(coproductLength.Out)

val h1 = 123 :: "foo" :: 0.8 :: true :: 'a' :: HNil
h1.apply(Nat._2)

h1.drop(Nat._1)
h1.take(Nat._3).drop(Nat._1)
h1.updatedAt(Nat._1, "bar").updatedAt(Nat._2, false)

val p = Person("james", 33, List("Cav", "Mia"))

"adbc" :: p :: 33 :: true :: HNil


import shapeless.syntax.std.tuple._
(1, "foo", true, 20.5).tail

case class Address(street: String, city: String, postcode: String)
case class Person3(name: String, age: Int, address: Address)

val ageLens = lens[Person3].age
val addressLens = lens[Person3].address

val person = Person3("Joe Grey", 38, Address("Southover Street", "Brighton", "BN2 9UA"))

val address1 = addressLens.get(person)
//typed[Address](address1)
val age1 = ageLens.get(person)
assert(age1 == 38)

val person2 = ageLens.set(person)(40)
assert(person2.age == 40)

val cityLens = lens[Address].city
val cityOfPersonLens = cityLens compose addressLens
cityOfPersonLens.get(person)

val person3 = cityOfPersonLens.set(person)("Chiago")
