import cats.data.Reader

case class Cat(name: String, favoriteFood: String)

val catName: Reader[Cat, String] = Reader(cat => cat.name)

catName.run(Cat("james", "Fish"))

val greetKitty: Reader[Cat, String] = catName.map(name => s"Hello, ${name}")

val g = greetKitty.run(Cat("King", "kingfish"))

val s: String = g

val feedKitty: Reader[Cat, String] =
  Reader(cat => s"Have a nice bowl of ${cat.favoriteFood}")

val greetAndFeed: Reader[Cat, String] = 
  for {
    msg1 <- greetKitty
    msg2 <- feedKitty
  } yield s"$msg1 $msg2"

greetAndFeed(Cat("Garfield", "lassage"))
greetAndFeed.run(Cat("Garfield", "lassage"))

greetAndFeed(Cat("Ade", "dog food"))

// Exercise
case class Db(
             usernames: Map[Int, String],
             password: Map[String, String]
             )

type DbReader[T] = Reader[Db, T]

def  findUsername(userId: Int): DbReader[Option[String]] =
  Reader(db => db.usernames.get(userId))

def checkPassword(username: String, password: String): DbReader[Boolean] = {
  Reader(db => {
    //db.password.find(d => d._1 == username && d._2 == password).isDefined
    db.password.get(username).contains(password)
  })
}

import cats.syntax.applicative._

def checkLogin(userId: Int, password: String): DbReader[Boolean] = for {
  username<- findUsername(userId)
  bool <- username.map { username =>
    checkPassword(username, password)
  } .getOrElse {
    false.pure[DbReader]
  }
} yield bool

val db = Db(
  Map(
    1 ->"dade",
    2 -> "kate",
    3 -> "margo"
  ),
  Map(
    "dade" -> "zercool",
    "kate" -> "acidburn",
    "margo" -> "secret"
  )
)

checkLogin(1, "zercool").run(db)

checkLogin(2, "acid").run(db)

checkLogin(4, "abc").run(db)
