package shapeless_tut.parser

/**
  * http://www.cakesolutions.net/teamblogs/automatic-type-class-derivation-with-shapeless-part-two
  */
trait LabelledParser[A] {
  def parse(args: List[String]): A
}

object LabelledParser {
  import shapeless.LabelledGeneric
  import shapeless.{HList, HNil, ::}
  import shapeless.{Lazy, Witness}
  import shapeless.labelled.{FieldType, field}

  private def create[A](thunk: List[String] => A): LabelledParser[A] = new LabelledParser[A] {
    override def parse(args: List[String]): A = thunk(args)
  }

  def apply[A](implicit st: Lazy[LabelledParser[A]]) = st.value

  implicit def genericParser[A, R <: HList](
                                           implicit
                                           gen: LabelledGeneric.Aux[A, R],
                                           parser: Lazy[LabelledParser[R]]
                                           ): LabelledParser[A] =
    create(args => gen.from(parser.value.parse(args)))

  implicit def hlistParser[K <: Symbol, H, T <: HList](
                                                      implicit
                                                      hParser: Lazy[LabelledParser[FieldType[K, H]]],
                                                      tParser: LabelledParser[T]
                                                      ): LabelledParser[FieldType[K, H] :: T] = {
    create { args =>
      val hv = hParser.value.parse(args)
      val tv = tParser.parse(args.tail)
      hv :: tv
    }
  }

  implicit def stringParser[K <: Symbol](
                                        implicit
                                        witness: Witness.Aux[K]
                                        ): LabelledParser[FieldType[K, String]] = {
    val name = witness.value.name
    create { args =>
      val arg = args.dropWhile(a => a != s"--$name").tail.head
      field[K](arg)
    }
  }

  implicit def intParser[K <: Symbol](
                                          implicit
                                          witness: Witness.Aux[K]
                                        ): LabelledParser[FieldType[K, Int]] = {
    val name = witness.value.name
    create { args =>
      val arg = args.dropWhile(a => a != s"--$name").tail.head.toInt
      field[K](arg)
    }
  }

  implicit def boolParser[K <: Symbol](
                                          implicit
                                          witness: Witness.Aux[K]
                                        ): LabelledParser[FieldType[K, Boolean]] = {
    val name = witness.value.name
    create { args =>
      val arg = args.find(a => a == s"--$name").isDefined
      field[K](arg)
    }
  }

  implicit val hnilParser: LabelledParser[HNil] = {
    create(args => HNil)
  }
}
