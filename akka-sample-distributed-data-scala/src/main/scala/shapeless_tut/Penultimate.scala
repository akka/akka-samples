package shapeless_tut

import shapeless._
import shapeless.ops.hlist

trait Penultimate[L] {
  type Out

  def apply(l: L): Out
}

object Penultimate {
  type Aux[L, O] = Penultimate[L] {
    type Out = O
  }

  def apply[L](implicit p: Penultimate[L]): Aux[L, p.Out] = p

  implicit def hlistPenultimate[L <: HList, M <: HList, O](
                                                          implicit
                                                          init: hlist.Init.Aux[L, M],
                                                          last: hlist.Last.Aux[M, O]
                                                          ): Penultimate.Aux[L, O] =
    new Penultimate[L] {
      type Out = O

      override def apply(l: L): O = last.apply(init.apply(l))
    }
}

object PenultimateApp {
  type BigList = String :: Int :: Boolean :: Double :: HNil

  type TinyList = String :: HNil

  implicit class PenulimateOps[A](a: A) {
    def penultimate(implicit inst: Penultimate[A]): inst.Out = inst.apply(a)
  }

  implicit def genericPenultimate[A, R, O](
                                          implicit
                                          generic: Generic.Aux[A, R],
                                          penultimate: Penultimate.Aux[R, O]
                                          ): Penultimate.Aux[A, O] = new Penultimate[A] {
    type Out = O

    override def apply(l: A): O = penultimate.apply(generic.to(l))
  }
  
  def main(args: Array[String]): Unit = {
    val bigList: BigList = "foo bar" :: 246 :: true :: 400.8 :: HNil

    println(Penultimate[BigList].apply(bigList))
    println(bigList.penultimate)

    val tinyList: TinyList = "James" :: HNil

    //println(Penultimate[TinyList].apply(tinyList))
    println(Employee("James", 23, true).penultimate)
  }
}