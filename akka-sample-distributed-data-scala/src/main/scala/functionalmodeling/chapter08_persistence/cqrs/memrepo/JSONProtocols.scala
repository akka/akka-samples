package functionalmodeling.chapter08_persistence
package cqrs
package memrepo

import spray.json._
import DefaultJsonProtocol._
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}

object JSONProtocols {
  implicit val balanceFormat = jsonFormat1(Balance)

  implicit object DateTimeFormat extends RootJsonFormat[DateTime] {
    val formatter = ISODateTimeFormat.basicDateTimeNoMillis()

    override def write(obj: DateTime) = JsString(formatter.print(obj))

    override def read(json: JsValue) = json match {
      case JsString(s) => try {
        formatter.parseDateTime(s)
      } catch {
        case t: Throwable => error(s)
      }

      case _ =>
        error(json.toString)
    }

    def error(v: Any): DateTime = {
      val example = formatter.print(0)
      deserializationError(f"'$v' is not a valid date value. Dates must be in compact ISO-8601 format, e.g. '$example'")
    }
  }

  implicit val accountFormat = jsonFormat5(Account.apply)

  implicit object OpenedFormat extends RootJsonFormat[Opened] {
    override def write(o: Opened) = JsObject(
      "no" -> JsString(o.no),
    )
  }
}
