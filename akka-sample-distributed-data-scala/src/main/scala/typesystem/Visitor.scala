package typesystem

import java.util.Date

sealed trait Visitor {
  def id: String
  def createdAt: Date
  def age: Long = new Date().getTime - createdAt.getTime
}

object Visitor {
  import JsWriter._
  implicit object AnonymousWriter extends JsWriter[Anonymous] {
    override def write(a: Anonymous) = JsObject(
      Map("id" -> a.id.toJson, "createdAt" -> a.createdAt.toJson, "age" -> a.age.toJson)
    )
  }

  implicit object UserWriter extends JsWriter[User] {
    override def write(a: User) = JsObject(
      Map(
        "id" -> a.id.toJson, "email" -> a.email.toJson, "createdAt" -> a.createdAt.toJson, "age" -> a.age.toJson
      )
    )
  }

  implicit object VisitorWriter extends JsWriter[Visitor] {
    override def write(a: Visitor) = a match {
      case anon: Anonymous => AnonymousWriter.write(anon)
      case user: User => UserWriter.write(user)
    }
  }
}

final case class Anonymous(
                          id: String,
                          createdAt: Date = new Date()
                          ) extends Visitor
final case class User(
                     id: String,
                     email: String,
                     createdAt: Date = new Date()
                     ) extends Visitor
