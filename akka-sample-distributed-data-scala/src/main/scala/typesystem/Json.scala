package typesystem

import java.util.Date

/**
  * typeclass
  */
sealed trait JsValue {
  def stringify: String
}

final case class JsObject(values: Map[String, JsValue]) extends JsValue {
  override def stringify = values.map { case (k, v) =>
      s""""$k":${v.stringify}"""
  }.mkString("{", ",", "}")
}

final case class JsString(value: String) extends JsValue {
  override def stringify = "\"" + value.replaceAll("\\|\"", "\\\\$1") + "\""
}

trait JsWriter[A] {
  def write(a: A): JsValue
}

object JsWriter {
  implicit class JsWriterSyntax[A](a: A) {
    def toJson(implicit writer: JsWriter[A]) = writer.write(a)
  }

  implicit object StringWriter extends JsWriter[String] {
    override def write(a: String) = JsString(a)
  }

  implicit object DateWriter extends JsWriter[Date] {
    override def write(a: Date) = JsString(a.toString)
  }

  implicit object LongWriter extends JsWriter[Long] {
    override def write(a: Long) = JsString(a.toString)
  }
}

object JsUtil {
  def toJson[A](a: A)(implicit writer: JsWriter[A]): JsValue = writer.write(a)
}

object JsonApp {

  def main(args: Array[String]): Unit = {
    val json = JsObject(
      Map("foo" -> JsString("a"), "bar" -> JsString("b"), "baz" -> JsString("c"))
    )

    val result = json.stringify
    println(result)

    //import JsUtil._
    import JsWriter._
    val visitors: Seq[Visitor] = Seq(Anonymous("001", new Date), User("003", "james@nba.com", new Date))

    //val jsons = visitors.map(toJson[Visitor])
    val jsons = visitors.map(_.toJson)
    println(jsons)
    println(jsons.map(_.stringify))
  }
}
