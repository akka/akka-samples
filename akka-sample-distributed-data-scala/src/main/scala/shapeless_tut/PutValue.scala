package shapeless_tut

import shapeless.labelled.FieldType
import shapeless._

/**
  * TODO
  */
trait PutValue
case class ObjectPut(fields: List[(String, PutValue)]) extends PutValue
case class ArrayPut(items: List[PutValue]) extends PutValue
case class StringPut(value: String) extends PutValue
case class PutInt(value: Int) extends PutValue
case class PutLong(value: Long) extends PutValue
case class PutDouble(value: Double) extends PutValue
case class PutBoolean(value: Boolean) extends PutValue
case object PutNull extends PutValue

trait PutEncoder[A] {
  def encode(value: A): PutValue
}

object PutEncoder {


  def createEncoder[A](fn: A => PutValue): PutEncoder[A] = new PutEncoder[A] {
    override def encode(value: A): PutValue = fn(value)
  }

  def createObjectEncoder[A](fn: A => ObjectPut): PutObjectEncoder[A] = new PutObjectEncoder[A] {
    override def encode(value: A): ObjectPut = fn(value)
  }

  implicit val stringEncoder: PutEncoder[String] = createEncoder(s => StringPut(s))
  implicit val intEncoder: PutEncoder[Int] = createEncoder(num => PutInt(num))
  implicit val longEncoder: PutEncoder[Long] = createEncoder(l => PutLong(l))
  implicit val doubleEncoder: PutEncoder[Double] = createEncoder(d => PutDouble(d))
  implicit val booleanEncoder: PutEncoder[Boolean] = createEncoder(bool => PutBoolean(bool))

  implicit def listEncoder[A](implicit encoder: PutEncoder[A]): PutEncoder[List[A]] = createEncoder { ls =>
    ArrayPut(ls.map(encoder.encode))
  }

  implicit def optionEncoder[A](implicit encoder: PutEncoder[A]): PutEncoder[Option[A]] = createEncoder { o =>
    o match {
      case Some(v) => encoder.encode(v)
      case None => PutNull
    }
  }
  implicit val hnilEncoder: PutObjectEncoder[HNil] = createObjectEncoder(hnil => ObjectPut(Nil))

  implicit def hlistEncoder[K <: Symbol, H, T <: HList](
                                                       implicit
                                                       witness: Witness.Aux[K],
                                                       hEncoder: Lazy[PutEncoder[H]],
                                                       tEncoder: PutObjectEncoder[T]
                                                       ): PutObjectEncoder[FieldType[K, H] :: T] = createObjectEncoder {
    case h :: t =>
      val hField = witness.value.name -> hEncoder.value.encode(h)
      val tFields = tEncoder.encode(t).fields
      ObjectPut(hField :: tFields)
  }

  implicit def genericEncoder[A, R <: HList](
                                        implicit
                                        gen: LabelledGeneric.Aux[A, R],
                                        encoder: Lazy[PutEncoder[R]]
                                        ): PutEncoder[A] = createEncoder { value =>
    encoder.value.encode(gen.to(value))
  }
}

trait PutObjectEncoder[A] extends PutEncoder[A] {
  def encode(value: A): ObjectPut
}

object PutApp {

  val Item_Separator: String = """//@//"""
  val Field_Separator: String = """/@@/"""
  def encode[A](value: A)(implicit enc: PutEncoder[A]): PutValue = enc.encode(value)

  def stringify(pv: PutValue): String = pv match {
    case ObjectPut(fields) => fields.filter(!_._2.isInstanceOf[PutNull.type ]).map(stringifyField).mkString(Item_Separator)
    case ArrayPut(items) => items.map(stringify).mkString(Field_Separator)
    case StringPut(s) => s
    case PutInt(v) => v.toString
    case PutLong(v) => v.toString
    case PutDouble(v) => v.toString
    case PutBoolean(v) => v.toString
    case PutNull => ""
  }

  def stringifyField(field: (String, PutValue)): String = {
    val (name, value) = field
    s"$name$Field_Separator${stringify(value)}"
  }

  def toPut(pv: PutValue): List[(String, String)] = {
    val fields = stringify(pv)
    val kvs = fields.split(Item_Separator)
    val nfs = kvs.collect { case kv =>
      println(kv)
      val t = kv.split(Field_Separator) 
      (t(0), t(1))
    }
    nfs.toList
  }

  def main(args: Array[String]): Unit = {
    val e = Employee("James", 23, true)

    val p = encode[Employee](e)
    val r = toPut(p)
    println(r)

    val e2 = Employee2("James", 23, true, Some("Cav"))
    val p2 = encode(e2)
    val r2 = toPut(p2)
    println(r2)

    val e3 = Employee2("James", 23, true, None)
    val p3 = encode(e3)
    val r3 = toPut(p3)
    println(r3)
  }
}