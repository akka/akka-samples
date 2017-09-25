package shapeless_guice

import shapeless.{:+:, ::, CNil, Coproduct, Generic, HList, HNil, Inl, Inr, Lazy, TypeClass, TypeClassCompanion}

/**
  * http://www.cakesolutions.net/teamblogs/solving-problems-in-a-generic-way-using-shapeless
  */
trait Depth[T] {
  def depth(t: T): Int
}

object Depth {



  def createDepth[A](func: A => Int): Depth[A] = new Depth[A] {
    override def depth(t: A): Int = func(t)
  }

  implicit def stringDepth: Depth[String] = createDepth { str => 1 }

  implicit def intDepth: Depth[Int] = createDepth(_ => 1)

  implicit def listDepth[A](implicit elementDepth: Depth[A]): Depth[List[A]] = createDepth { list =>
    if (list.isEmpty) 1 else list.map(elementDepth.depth).max + 1
  }
  /*
  implicit def coordinateDepth(implicit iDepth: Depth[Int]): Depth[Coordinate] = createDepth { c =>
    val d = iDepth.depth(c.x) max iDepth.depth(c.y)
    d + 1
  }

  implicit def rectangleDepth(implicit coordDepth: Depth[Coordinate]): Depth[Rectangle] = createDepth { r =>
    val d = coordDepth.depth(r.corner1) max coordDepth.depth(r.corner2)
    d + 1
  }

  implicit def circleDepth(implicit iDepth: Depth[Int], coordDepth: Depth[Coordinate]): Depth[Circle] = createDepth { t =>
    iDepth.depth(t.radius) max coordDepth.depth(t.center) + 1

  }

  implicit def triangleDepth(implicit coordDepth: Depth[Coordinate]): Depth[Triangle] = createDepth { t =>

    val max = coordDepth.depth(t.corner1) max coordDepth.depth(t.corner2) max coordDepth.depth(t.corner2)
    max + 1

  }

  implicit def shapeDepth(implicit cdepth: Depth[Circle], rectDepth: Depth[Rectangle], triDepth: Depth[Triangle]): Depth[Shape] = createDepth {
    case c: Circle =>
      cdepth.depth(c)
    case r: Rectangle =>
      rectDepth.depth(r)
    case t: Triangle =>
      triDepth.depth(t)
  }

  implicit def surfaceDepth(implicit sdepth: Depth[String], shapeDepth: Depth[Shape]): Depth[Surface] = createDepth { s =>
    val m = sdepth.depth(s.name) max shapeDepth.depth(s.shape1) max shapeDepth.depth(s.shape2)
    m + 1
  }
*/
  implicit val hnilDepth: Depth[HNil] = createDepth(_ => 0)

  implicit def hlistDepth[H, T <: HList](implicit hDepth: Lazy[Depth[H]], tDepth: Depth[T]): Depth[H :: T] = createDepth {
      case h :: t =>
        (hDepth.value.depth(h) + 1) max tDepth.depth(t)
  }

  implicit val cnilDepth: Depth[CNil] = createDepth(_ => 0)

  implicit def coproductDepth[H, T <: Coproduct](
                                                     implicit
                                                     hDepth: Lazy[Depth[H]],
                                                     tDepth: Depth[T]

                               ): Depth[H :+: T] = createDepth {
    case Inl(h) =>
      hDepth.value.depth(h)
    case Inr(t) =>
      tDepth.depth(t)
  }

  implicit def genericDepth[A, H](implicit gen: Generic.Aux[A, H], depth: Lazy[Depth[H]]): Depth[A] = createDepth { a =>
    depth.value.depth(gen.to(a))
  }
}

/*object Depth1 extends TypeClassCompanion[Depth] {
  def createDepth[A](func: A => Int): Depth[A] = new Depth[A] {
    override def depth(t: A): Int = func(t)
  }
  implicit object typeClass extends TypeClass[Depth] {
    override def emptyProduct: Depth[HNil] = createDepth(_ => 0)

    override def product[H, T <: HList](ch: Depth[H], ct: Depth[T]): Depth[H :: T] = createDepth {
      case h :: t =>
        (ch.depth(h) + 1) max ct.depth(t)
    }

    override def emptyCoproduct: Depth[CNil] = createDepth(_ => 0)

    override def coproduct[L, R <: Coproduct](cl: => Depth[L], cr: => Depth[R]): Depth[:+:[L, R]] = createDepth {
      case Inl(h) =>
        cl.depth(h)
      case Inr(t) =>
        cr.depth(t)
    }

    override def project[F, G](instance: => Depth[G], to: (F) => G, from: (G) => F): Depth[F] = createDepth { f =>
      instance.depth(to(f))
    }
  }
}*/
object DepthApp {

  case class Coordinate(x: Int, y: Int)
  sealed trait Shape2
  case class Circle2(radius: Int, center: Coordinate) extends Shape2
  case class Rectangle2(corner1: Coordinate, corner2: Coordinate) extends Shape2
  case class Triangle2(corner1: Coordinate, corner2: Coordinate, corner3: Coordinate) extends Shape2

  case class Surface(name: String, shape1: Shape2, shape2: Shape2)

  def depth[A](value: A)(implicit depth: Depth[A]): Int = depth.depth(value)
  def main(args: Array[String]): Unit = {
    import Depth._
    val c1 = Coordinate(1, 2)
    val c2 = Coordinate(3, 4)
    val c3 = Coordinate(5, 6)
    val c4 = Coordinate(7, 8)

    val circle1: Shape2 = Circle2(2, c1)
    val rectangle1: Shape2 = Rectangle2(c2, c3)
    val triangle1: Shape2 = Triangle2(c1, c2, c3)

    val surface1 = Surface("surface1", circle1, rectangle1)

    println(s"Coordinate depth: ${depth(c1)}")
    println(s"Circle depth: ${depth[Shape2](circle1)}")
    println(s"Rectangle1 depth: ${depth(rectangle1)}")
    println(s"Triangle1 depth: ${depth(triangle1)}")
    println(s"Surface1 depth: ${depth(surface1)}")
    
  }
}