package typesystem

/**
  * Typeclass
  */
trait Equal[A] {
  def equal(v1: A, v2: A): Boolean

}

object Equal {
  def apply[A](implicit eq: Equal[A]): Equal[A] = eq

  implicit class ToEqual[A](in: A) {
    def ===(other: A)(implicit eq: Equal[A]) = eq.equal(in, other)
  }
}
case class Person(name: String, email: String)

object EmailEqual extends Equal[Person] {
  override def equal(v1: Person, v2: Person) = v1.email == v2.email
}

object NameEmailEqual extends Equal[Person] {
  override def equal(v1: Person, v2: Person) =
    v1.email == v2.email && v1.name == v2.name
}

object Eq {
  def apply[A](a1: A, a2: A)(implicit eq: Equal[A]): Boolean =
    eq.equal(a1, a2)
}

object EqApp {

  implicit val caseInsensitiveEquals = new Equal[String] {
    override def equal(v1: String, v2: String): Boolean = v1.toLowerCase == v2.toLowerCase
  }

  def main(args: Array[String]): Unit = {
    implicit val ee = EmailEqual
    //println(Eq(Person("Noel2", "noel@example.com"), Person("Noel", "noel@example.com")))
    println(Equal[Person].equal(Person("Noel2", "noel@example.com"), Person("Noel", "noel@example.com")))

    import Equal._
    println("abcd" === "ABCD")
  }
}