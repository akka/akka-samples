package shapeless_tut.migration

import cats.Monoid
import shapeless._
import shapeless.labelled.{FieldType, field}
import shapeless.ops.hlist
import shapeless_tut._

trait Migration[A, B] {
  def apply(a: A): B
}

object Migration {
  implicit class MigrationOps[A](a: A) {
    def migrationTo[B](implicit m: Migration[A, B]) = m.apply(a)
  }

  implicit def genericMigration[A, B,
                                ARepr <: HList, BRepr <: HList,
                                Unaligned <: HList](
                                                                     implicit
                                                                     aGen: LabelledGeneric.Aux[A, ARepr],
                                                                     bGen: LabelledGeneric.Aux[B, BRepr],
                                                                     inter: hlist.Intersection.Aux[ARepr, BRepr, Unaligned],
                                                                     align: hlist.Align[Unaligned, BRepr]
                                                                     ): Migration[A, B] = new Migration[A, B] {
    override def apply(a: A): B = bGen.from(align.apply(inter.apply(aGen.to(a))))
  }

  /**
    * 1. use LabelledGeneric to convert A to its generic representation
    * 2. use Intersection to calculate an HList of fields common to A and B
    * 3. calculate the types of fields that appear in B but not in A
    * 4. use Monoid to calculate to default value of the type from step 3
    * 5. append the common fields from step 2 to new field from step 4
    * 6. use Align to reorder the fields from step 5 in the same order as B
    * 7. use LabelledGeneric to convert the output of step 6 to B
    */
  implicit def genericMigration[A, B,
  ARepr <: HList, BRepr <: HList,
  Common <: HList, Added <: HList, Unaligned <: HList](
                                                        implicit
                                                        aGen: LabelledGeneric.Aux[A, ARepr],
                                                        bGen: LabelledGeneric.Aux[B, BRepr],
                                                        inter: hlist.Intersection.Aux[ARepr, BRepr, Common],
                                                        diff: hlist.Diff.Aux[BRepr, Common, Added],
                                                        //monoid: Monoid[Added],
                                                        empty: Empty[Added],
                                                        prepend: hlist.Prepend.Aux[Added, Common, Unaligned],
                                                        align: hlist.Align[Unaligned, BRepr]
                                                      ): Migration[A, B] =
    new Migration[A, B] {
      override def apply(a: A): B = {
        val aRepr = aGen.to(a)
        val common = inter(aRepr)
        val added = empty.value
        val unaligned = prepend(added, common)
        val bRepr = align(unaligned)

        bGen.from(bRepr)
        /*bGen.from(
          align(
            prepend(
              monoid.empty, inter(aGen.to(a))
            )
          )
        )*/
      }
    }
}

case class Empty[A](value: A)

object Empty {
  implicit def monoidEmpty[A](implicit monoid: Monoid[A]): Empty[A] = Empty(monoid.empty)

  implicit def hnilEmpty: Empty[HNil] = Empty(HNil)

  implicit def hlistEmpty[K <: Symbol, H, T <: HList](
                                                     implicit
                                                     hEmpty: Lazy[Empty[H]],
                                                     tEmpty: Empty[T]
                                                     ): Empty[FieldType[K, H] :: T] =
    Empty(field[K](hEmpty.value.value) :: tEmpty.value)
}
object MigrationApp {

  /*def createMonoid[A](zero: A)(add: (A, A) => A): Monoid[A] = new Monoid[A] {
    override def empty: A = zero

    override def combine(x: A, y: A): A = add(x, y)
  }*/
/*

  implicit val hnilMonoid: Monoid[HNil] = createMonoid[HNil](HNil)((x, y) => HNil)

  implicit def emptyHList[K <: Symbol, H, T <: HList](
                                                     implicit
                                                     hMonoid: Lazy[Monoid[H]],
                                                     tMonoid: Monoid[T]
                                                     ): Monoid[FieldType[K, H] :: T] =
    createMonoid(field[K](hMonoid.value.empty) :: tMonoid.empty) {
      (x, y) =>
        field[K](hMonoid.value.combine(x.head, y.head)) :: tMonoid.combine(x.tail, y.tail)
    }

*/


  def main(args: Array[String]): Unit = {
    import Migration._
    val iceCream1 = IceCreamV1("Sundae", 2, true)
    println(s"IceCreamV1: $iceCream1")

    /*val r1 = iceCream1.migrationTo[IceCreamV2a]
    println(s"IceCreamV2a: $r1")

    val r2 = iceCream1.migrationTo[IceCreamV2b]
    println(s"IceCreamV2b: $r2")

    val r3 = iceCream1.migrationTo[IceCreamV2C]
    println(s"IceCreamV2c: $r3")*/
    
  }
}