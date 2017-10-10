package shapeless_tut

import shapeless.labelled.FieldType
import shapeless.{::, Generic, HList, HNil, Lazy, Witness}

/**
  * 把一个对象转成一个Map[String, String]
  */
trait FieldValues[A] {
  def toMap(value: A): Map[String, String]
}

object FieldValues {
  def createFV[A](fn: A => Map[String, String]): FieldValues[A] = new FieldValues[A] {
    override def toMap(value: A): Map[String, String] = fn(value)
  }

  implicit val stringFV: FieldValues[String] = createFV(s => Map("string" -> s))

  implicit val intFV: FieldValues[Int] = createFV(num => Map("int" -> num.toString))

  implicit val booleanFV: FieldValues[Boolean] = createFV(bool => Map("boolean" -> bool.toString))

  implicit def mapFV[A](implicit fv: FieldValues[A]): FieldValues[Map[A, A]] = createFV { map =>
    map.map {
      case (k, v) =>
        (k.toString, v.toString)
    }
  }

  implicit val hnilFV: FieldValues[HNil] = createFV(hnil => Map())

  implicit def hlistFV[K <: Symbol, H, T <: HList](
                                     implicit
                                                  witness: Witness.Aux[K],
                                     hfv: Lazy[FieldValues[H]],
                                     tfv: FieldValues[T]
                                     ): FieldValues[FieldType[K, H] :: T] = createFV {
    case h :: t =>
      val hf = Map(witness.value.name -> hfv.value.toMap(h).values.head)
      hf ++ tfv.toMap(t)
     //hfv.value.toMap(h) ++ tfv.toMap(t)
  }

  implicit def genericFV[A, R <: HList](
                                       implicit
                                       gen: Generic.Aux[A, R],
                                       fv: Lazy[FieldValues[R]]
                                       ): FieldValues[A] = createFV { list =>
    fv.value.toMap(gen.to(list))
  }
}

object FieldValuesApp {

  def fieldValues[A](value: A)(implicit fv: FieldValues[A]): Map[String, String] = fv.toMap(value)

  def main(args: Array[String]): Unit = {
    val e = Employee("James", 23, true)
    
    //val fv1 = fieldValues(e)
    //println(fv1)
  }
}