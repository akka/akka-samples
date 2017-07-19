import cats.data.Validated
import cats.syntax.either._
import cats.syntax.validated._
import cats.instances.list._
import cats.instances.string._
import cats.syntax.cartesian._
import cats.Cartesian

import scala.util.{Failure, Success, Try}

case class User(name: String, age: Int)

type ErrorOr[A] = Either[List[String], A]
type FromData = Map[String, String]
type AllErrorOr[A] = Validated[List[String], A]

def readName(name: String)(m: Map[String, String]): ErrorOr[String] = {
  for {
    v <- getValue(name)(m)
    n <- nonBlank(v)
  } yield n
}

def readAge(name: String)(m: Map[String, String]): ErrorOr[Int] = for {
  v <- getValue(name)(m)
  n <- nonBlank(v)
  p <- parseInt(n)
  ne <- nonNegative(p)
} yield ne

def getValue(name: String)(data: FromData): ErrorOr[String] =
  data.get(name).toRight(List(s"$name field not specified"))

/*def parseInt(str: String): ErrorOr[Int] = Try(str.trim.toInt) match {
  case Failure(_) => Left(List(s"$str is not a numeric"))
  case Success(r) => r.asRight[List[String]]
}*/
def parseInt(data: String): ErrorOr[Int] =
  Right(data).flatMap { s =>
    Either.catchOnly[NumberFormatException](s.toInt)
  }.leftMap(_ => List(s"$data must be an integer"))
  //Try(str.trim.toInt).toEither.fold(_ => List(s"$str is not a numeric"), x => x)
  //Validated.catchOnly[NumberFormatException](str.trim.toInt)
  //  .toEither.fold(_ => List(s"$str is not a numeric"), _)

def nonBlank(str: String): ErrorOr[String] = Right(str).ensure(List(
  s"$str cannot be blank"
))(_.nonEmpty)
  /*if (str.isEmpty) List("string is empty").asLeft[String]
  else str.asRight[List[String]]*/

def nonNegative(num: Int): ErrorOr[Int] =
  Right(num).ensure(
    List(s"$num must be non-negative")
  )(_ >= 0)
  //num.valid[List[String]].ensure(List("Negative!"))(_ > 0).toEither

def getUser(nameKey: String, ageKey: String)(data: FromData): AllErrorOr[User] = (
  Validated.fromEither(readName(nameKey)(data)) |@|
  Validated.fromEither(readAge(ageKey)(data))
).map(User.apply)//.tupled//.bimap(l => Left(l), User.apply(_))

val name = "abcname"
val ageKey = "abcage"
val data = Map("abcname" -> "James", "abcage" -> "30", "nonname" -> "", "negage" -> "-20")
getUser(name, ageKey)(data)
getUser("nonname", "negage")(data)

getValue("name")(Map())
getValue("name")(Map("name" -> "Wade"))
