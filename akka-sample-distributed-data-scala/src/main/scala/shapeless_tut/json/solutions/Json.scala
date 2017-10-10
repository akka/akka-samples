package shapeless_tut.json.solutions

import shapeless.{::, HList, HNil}
import shapeless.{:+:, CNil, Coproduct, Inl, Inr}
import shapeless.Lazy
import shapeless.{LabelledGeneric, Witness}
import shapeless.labelled.FieldType
import shapeless_tut._


/** JSON ADT */
sealed abstract class Json
final case class JsonObject(fields: List[(String, Json)]) extends Json
final case class JsonArray(items: List[Json]) extends Json
final case class JsonString(value: String) extends Json
final case class JsonNumber(value: Double) extends Json
final case class JsonBoolean(value: Boolean) extends Json
case object JsonNull extends Json


/** Stringification methods */
object Json {
  def stringify(json: Json): String = json match {
    case JsonObject(fields) => "{" + fields.map(stringifyField).mkString(",") + "}"
    case JsonArray(items)   => "[" + items.map(stringify).mkString(",") + "]"
    case JsonString(value)  => "\"" + escape(value) + "\""
    case JsonNumber(value)  => value.toString
    case JsonBoolean(value) => value.toString
    case JsonNull           => "null"
  }

  private def stringifyField(field: (String, Json)): String = {
    val (name, value) = field
    escape(name) + ":" + stringify(value)
  }

  private def escape(str: String): String =
    "\"" + str.replaceAll("\"", "\\\\\"") + "\""
}



/**
  * Type class for encoding a value of type A as JSON.
  */
trait JsonEncoder[A] {
  def encode(value: A): Json
}

/**
  * Specialization of JsonEncoder
  * for encoding as a JSON object.
  *
  * We introduce this because
  * the encoder for :: has to asemble an object
  * from fields form the head and tail.
  * Having JsonObjectEncoder avoids annoying
  * pattern matching to ensure the encoded tail
  * is indeed an object.
  */
trait JsonObjectEncoder[A] extends JsonEncoder[A] {
  def encode(value: A): JsonObject
}




object JsonEncoder {
  /** Helper: create a JsonEncoder from a plain function */
  def pure[A](func: A => Json): JsonEncoder[A] =
    new JsonEncoder[A] {
      def encode(value: A): Json =
        func(value)
    }

  /** Helper: create a JsonObjectEncoder from a plain function */
  def pureObj[A](func: A => JsonObject): JsonObjectEncoder[A] =
    new JsonObjectEncoder[A] {
      def encode(value: A): JsonObject =
        func(value)
    }

  // JsonEncoder instances for primitive types:

  implicit val stringEncoder: JsonEncoder[String] =
    pure(str => JsonString(str))

  implicit val intEncoder: JsonEncoder[Int] =
    pure(num => JsonNumber(num))

  implicit val doubleEncoder: JsonEncoder[Double] =
    pure(num => JsonNumber(num))

  implicit val booleanEncoder: JsonEncoder[Boolean] =
    pure(bool => JsonBoolean(bool))

  implicit def optionEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] =
    pure(opt => opt.map(encoder.encode).getOrElse(JsonNull))

  implicit def listEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] =
    pure(list => JsonArray(list.map(encoder.encode)))

  // JsonEncoder instances for HLists.
  //
  // Notice that hlistEncoder produces an instance for:
  //
  //     JsonObjectEncoder[FieldType[K, H] :: T]
  //
  // FieldType[K, H] represents a type H tagged with K.
  // LabelledGeneric tags its component types
  // with the Symbolic literal types of the field names,
  // so we can use a Witness.Aux[K] to retrieve
  // the field name as a value.

  implicit val hnilEncoder: JsonObjectEncoder[HNil] =
    pureObj(hnil => JsonObject(Nil))

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](
                                                         implicit
                                                         witness: Witness.Aux[K],
                                                         hEncoder: Lazy[JsonEncoder[H]],
                                                         tEncoder: JsonObjectEncoder[T]
                                                       ): JsonObjectEncoder[FieldType[K, H] :: T] =
    pureObj {
      case h :: t =>
        val hField  = witness.value.name -> hEncoder.value.encode(h)
        val tFields = tEncoder.encode(t).fields
        JsonObject(hField :: tFields)
    }

  // JsonEncoder instances for Coproducts:
  //
  // The notes above for hlistEncoder also apply to
  // coproductEncoder, except that
  // K represents a type name, not a field name.

  implicit val cnilEncoder: JsonObjectEncoder[CNil] =
    pureObj(cnil => ???)

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](
                                                                 implicit
                                                                 witness: Witness.Aux[K],
                                                                 hEncoder: Lazy[JsonEncoder[H]],
                                                                 tEncoder: JsonObjectEncoder[T]
                                                               ): JsonObjectEncoder[FieldType[K, H] :+: T] =
    pureObj {
      case Inl(h) => JsonObject(List(witness.value.name -> hEncoder.value.encode(h)))
      case Inr(t) => tEncoder.encode(t)
    }

  // JsonEncoder instance for LabelledGeneric:

  implicit def genericEncoder[A, R](
                                     implicit
                                     gen: LabelledGeneric.Aux[A, R],
                                     enc: Lazy[JsonEncoder[R]]
                                   ): JsonEncoder[A] =
    pure(a => enc.value.encode(gen.to(a)))
}

object Main extends Demo {
  // Entry point for JsonEncoder:

  def encodeJson[A](value: A)(implicit encoder: JsonEncoder[A]): Json =
    encoder.encode(value)
/*

  sealed trait Shape
  final case class Rectangle(width: Double, height: Double) extends Shape
  final case class Circle(radius: Double) extends Shape
*/

  val shapes: List[Shape] =
    List(
      Rectangle(1, 2),
      Circle(3),
      Triangle(2, 4, 6),
      Rectangle(4, 5),
      Triangle(1, 4, 7),
      Circle(6)
    )

  val optShapes: List[Option[Shape]] =
    List(
      Some(Rectangle(1, 2)),
      Some(Circle(3)),
      None,
      Some(Rectangle(4, 5)),
      Some(Circle(6)),
      None
    )

  println("Shapes " + shapes)
  println("Shapes as JSON: " + Json.stringify(encodeJson(shapes)))
  println("Optional shapes " + optShapes)
  println("Optional shapes as JSON: " + Json.stringify(encodeJson(optShapes)))

  val iceCream = IceCream("Sundae", 1, false)
  println(Json.stringify(encodeJson(iceCream)))
}
