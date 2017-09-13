package shapeless_guice

import shapeless._

trait Second[L <: HList] {
  type Out

  def apply(value: L): Out
}

object Second {
  type Aux[L <: HList, O] = Second[L] {
    type Out = O
  }

  def apply[L <: HList](implicit inst: Second[L]): Aux[L, inst.Out] = inst

  implicit def hlistSecond[A, B, Rest <: HList]: Aux[A :: B :: Rest, B] = new Second[A :: B :: Rest] {
    type Out = B
    override def apply(value: ::[A, ::[B, Rest]]): Out = value.tail.head
  }
}

object SecondApp {
  def main(args: Array[String]): Unit = {

    val second1 = Second[String :: Boolean :: Int :: HNil]
    val second2 = Second[String :: Int :: Boolean :: HNil]

    println(second1("foo" :: true :: 123 :: HNil))
    println(second2("bar" :: 321 :: false :: HNil))
  }
}