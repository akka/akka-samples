package shapeless_tut

import shapeless.{::, Generic, HList, HNil, Lazy}

import scala.util.Random

/**
  * https://automateddeveloper.blogspot.co.uk/2017/06/generic-programming-in-scala-with.html
  */
trait Generator[A] {
  def generate: A
}

object Generator {
  def generate[A](implicit gen: Generator[A]): A  = gen.generate

  implicit def intGenerator = new Generator[Int] {
    override def generate = Random.nextInt
  }

  implicit def doubleGenerator = new Generator[Double] {
    override def generate = Random.nextDouble
  }

  implicit def StringGenerator = new Generator[String] {
    val loremWords = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.".split(" ")
    override def generate = Random.shuffle(loremWords.toList.take(5)).mkString(" ")
  }

  implicit def booleanGenerator = new Generator[Boolean] {
    override def generate = Random.nextBoolean
  }

  implicit def hnilGenerator = new Generator[HNil] {
    override def generate: HNil = HNil
  }

  implicit def hconsGenerator[H, T <: HList](
                                            implicit
                                            hGen: Generator[H],
                                            tGen: Generator[T]
                                            ): Generator[H :: T] = new Generator[H :: T] {
    override def generate = hGen.generate :: tGen.generate
  }
  
  implicit def genericToGenerator[T, L <: HList](
                                                  implicit
                                                generic: Generic.Aux[T, L],
                                                  lGen: Generator[L]
                                                ): Generator[T] = {
    new Generator[T] {
      override def generate: T = generic.from(lGen.generate)
    }
  }
}

object GeneratorApp {
  case class Sample(a: Int, b: Boolean, c: Double, d: String, e: Double)

  def main(args: Array[String]): Unit = {
    import Generator._
    val g = generate[Sample]
    println(g)
  }
}