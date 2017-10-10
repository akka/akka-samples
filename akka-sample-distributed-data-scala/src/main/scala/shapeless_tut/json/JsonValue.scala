package shapeless_tut.json

import shapeless.labelled.FieldType
import shapeless._
import shapeless_tut._

sealed trait JsonValue
case class JsonObject(fields: List[(String, JsonValue)]) extends JsonValue
case class JsonArray(items: List[JsonValue]) extends JsonValue
case class JsonString(value: String) extends JsonValue
case class JsonNumber(value: Double) extends JsonValue
case class JsonBoolean(value: Boolean) extends JsonValue
case object JsonNull extends JsonValue

trait JsonEncoder[A] {
  def encode(value: A): JsonValue
}

object JsonEncoder {
  //def apply[A](implicit enc: JsonEncoder[A]): JsonEncoder[A] = enc

  def createEncoder[A](func: A => JsonValue): JsonEncoder[A] = new JsonEncoder[A] {
    override def encode(value: A): JsonValue = func(value)
  }

  def createObjectEncoder[A](fn: A => JsonObject): JsonObjectEncoder[A] = new JsonObjectEncoder[A] {
    override def encode(value: A): JsonObject = fn(value)
  }

  implicit val stringEncoder: JsonEncoder[String] = createEncoder(s => JsonString(s))

  implicit val doubleEncoder: JsonEncoder[Double] = createEncoder(d => JsonNumber(d))

  implicit val intEncoder: JsonEncoder[Int] = createEncoder(num => JsonNumber(num.toDouble))

  implicit val booleanEncoder: JsonEncoder[Boolean] = createEncoder(bool => JsonBoolean(bool))

  implicit def listEncoder[A](implicit enc: JsonEncoder[A]): JsonEncoder[List[A]] = createEncoder(list => JsonArray(list.map(enc.encode)))

  implicit def optionEncoder[A](implicit enc: JsonEncoder[A]): JsonEncoder[Option[A]] = createEncoder { opt =>  opt.map(enc.encode).getOrElse(JsonNull) }

  /*implicit val hnilEncoder: JsonObjectEncoder[HNil] = createObjectEncoder(nil => JsonObject(Nil))

  implicit def hlistObjectEncoder[K <: Symbol, H, T <: HList](
                                                               implicit
                                                               witness: Witness.Aux[K],
                                                               hEncoder: Lazy[JsonEncoder[H]],
                                                               tEncoder: JsonObjectEncoder[T]
                                                             ): JsonObjectEncoder[FieldType[K, H] :: T] = {
    val fieldName: String = witness.value.name
    createObjectEncoder { case h :: t =>
      val head = hEncoder.value.encode(h)
      val tail = tEncoder.encode(t)
      JsonObject((fieldName, head) :: tail.fields)
    }
  }
*/
  implicit val hnilEncoder: JsonObjectEncoder[HNil] =
    createObjectEncoder(hnil => JsonObject(Nil))

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](
                                                         implicit
                                                         witness: Witness.Aux[K],
                                                         hEncoder: Lazy[JsonEncoder[H]],
                                                         tEncoder: JsonObjectEncoder[T]
                                                       ): JsonObjectEncoder[FieldType[K, H] :: T] =
    createObjectEncoder {
      case h :: t =>
        val hField  = witness.value.name -> hEncoder.value.encode(h)
        val tFields = tEncoder.encode(t).fields
        JsonObject(hField :: tFields)
    }

  /*implicit val cnilObjectEncoder: JsonObjectEncoder[CNil] = createObjectEncoder(cnil => throw new Exception("Inconceivable!"))

  implicit def coproductObjectEncoder[K <: Symbol, H, T <: Coproduct](
                                                                       implicit
                                                                       witness: Witness.Aux[K],
                                                                       hEncoder: Lazy[JsonEncoder[H]],
                                                                       tEncoder: JsonObjectEncoder[T]
                                                                     ): JsonObjectEncoder[FieldType[K, H] :+: T] = {
    val typeName = witness.value.name
    createObjectEncoder {
      case Inl(h) =>
        JsonObject(List(typeName -> hEncoder.value.encode(h)))

      case Inr(t) =>
        tEncoder.encode(t)
    }
  }
*/
  implicit val cnilEncoder: JsonObjectEncoder[CNil] =
    createObjectEncoder(cnil => ???)

  implicit def coproductEncoder[K <: Symbol, H, T <: Coproduct](
                                                                 implicit
                                                                 witness: Witness.Aux[K],
                                                                 hEncoder: Lazy[JsonEncoder[H]],
                                                                 tEncoder: JsonObjectEncoder[T]
                                                               ): JsonObjectEncoder[FieldType[K, H] :+: T] =
    createObjectEncoder {
      case Inl(h) => JsonObject(List(witness.value.name -> hEncoder.value.encode(h)))
      case Inr(t) => tEncoder.encode(t)
    }
  implicit def genericEncoder[A, H <: HList](
                                                    implicit
                                                    generic: LabelledGeneric.Aux[A, H],
                                                    hEncoder: Lazy[JsonEncoder[H]]
                                                  ): JsonEncoder[A] = {
    createEncoder { value =>
      hEncoder.value.encode(generic.to(value))
    }
  }
}

object JsonValue {


}

trait JsonObjectEncoder[A] extends JsonEncoder[A] {
  def encode(value: A): JsonObject
}

object JsonApp {

  def encode[A](value: A)(implicit enc: JsonEncoder[A]): JsonValue = enc.encode(value)

  def main(args: Array[String]): Unit = {

    val iceCream = IceCream("Sundae", 1, false)

    //val gen = LabelledGeneric[IceCream].to(iceCream)

    //println(gen)

    val r = encode(iceCream)
    println(r)

    val shape: Shape = Circle(1.0)
    //println(encode(shape))

  }
}
