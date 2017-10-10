package shapeless_tut

import shapeless._
import shapeless.ops._

trait SizeOf[A] {
  def value: Int
}

object SizeOf {
  implicit def genericSizeOf[A, L <: HList, N <: Nat](
                                                     implicit
                                                     generic: Generic.Aux[A, L],
                                                     size: hlist.Length.Aux[L, N],
                                                     sizeToInt: ops.nat.ToInt[N]
                                                     ): SizeOf[A] = new SizeOf[A] {
    override def value: Int = sizeToInt.apply()
  }
}

object SizeOfApp {

  def sizeOf[A](implicit size: SizeOf[A]): Int = size.value

  def main(args: Array[String]): Unit = {
    val s = sizeOf[IceCream]
    println(s)

  }
}
