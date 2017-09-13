package shapeless_guice.random

import shapeless.ops.coproduct
import shapeless.ops.nat.ToInt
import shapeless.{:+:, ::, CNil, Coproduct, Generic, HList, HNil, Inl, Inr, Lazy, Nat}
import shapeless_guice.{IceCream, Shape}

trait Random[A] {
  def get: A
}

object Random {
  def createRandom[A](func: () => A): Random[A] = new Random[A] {
    override def get: A = func()
  }

  // Random numbers from 0 to 9
  implicit val intRandom: Random[Int] = createRandom { () =>
    scala.util.Random.nextInt(10)
  }

  // Random characters from A to Z
  implicit val charRandom: Random[Char] = createRandom { () =>
    ('A'.toInt + scala.util.Random.nextInt(26)).toChar
  }

  implicit val doubleRandom: Random[Double] = createRandom { () =>
    scala.util.Random.nextDouble()
  }

  implicit val booleanRandom: Random[Boolean] = createRandom { () =>
    scala.util.Random.nextBoolean()
  }

  implicit def stringRandom: Random[String] = createRandom { () =>
    val length = 5
    val cs = for (_ <- 1 to length) yield charRandom
    cs.map(_.get).mkString
  }

  implicit val hnilRandom: Random[HNil] = createRandom { () =>
    HNil
  }

  implicit def hlistRandom[H, T <: HList](
                                           implicit
                                         hRandom: Lazy[Random[H]],
                                           tRandom: Random[T]
                                         ): Random[H :: T] = createRandom { () =>
    hRandom.value.get :: tRandom.get
  }

  implicit val cnilRandom: Random[CNil] = createRandom(() =>
  throw new Exception("Inconcievable!"))

  implicit def coproductRandom[H, T <: Coproduct, N <: Nat](
                                                 implicit
                                                 hRandom: Lazy[Random[H]],
                                                 tRandom: Random[T],
                                                 tLength: coproduct.Length.Aux[T, N],
                                                 tLengthAsInt: ToInt[N]
                                                 ): Random[H :+: T] = createRandom { () =>
    val length = 1 + tLengthAsInt()
    val chooseH = scala.util.Random.nextDouble() < (1.0 / length)
    if (chooseH) Inl(hRandom.value.get) else Inr(tRandom.get)
  }

  implicit def genericRandom[A, R](
                                  implicit
                                  gen: Generic.Aux[A, R],
                                  random: Lazy[Random[R]]
                                  ): Random[A] = createRandom { () =>
    gen.from(random.value.get)
  }
}
object RandomApp {
  def random[A](implicit r: Random[A]): A = r.get

  def main(args: Array[String]): Unit = {
    for(i <- 1 to 5) println(random[Int])
    for (_ <- 1 to 5) println(random[Char])
    for (_ <- 1 to 5) println(random[Boolean])
    
    for (_ <- 1 to 5) println(random[IceCream])
    for (_ <- 1 to 100) println(random[Shape])
  }
}