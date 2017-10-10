package shapeless_tut.parser

/**
  * http://www.cakesolutions.net/teamblogs/automatic-type-class-derivation-with-shapeless-part-one
  */
trait Parser[A] {
  def parse(args: List[String]): A
}

object Parser {
  import shapeless.{::, Generic, HList, HNil, Lazy}

  private def create[A](thunk: List[String] => A): Parser[A] = {
    new Parser[A] {
      override def parse(args: List[String]): A = thunk(args)
    }
  }

  def apply[A](implicit st: Lazy[Parser[A]]): Parser[A] = st.value

  implicit def genericParser[A, R <: HList](
                                           implicit
                                           gen: Generic.Aux[A, R],
                                           parser: Lazy[Parser[R]]
                                           ): Parser[A] = {
    create(args => gen.from(parser.value.parse(args)))
  }

  implicit val stringParser: Parser[String] = {
    create(args => args.head)
  }

  implicit val intParser: Parser[Int] = {
    create(args => args.head.toInt)
  }

  implicit val boolParser: Parser[Boolean] = {
    create(args => args.head.toBoolean)
  }

  implicit val hnilParser: Parser[HNil] = {
    create(args => HNil)
  }

  implicit def hlistParser[H, T <: HList](
                                         implicit
                                         hParser: Lazy[Parser[H]],
                                         tParser: Parser[T]
                                         ): Parser[H :: T] = {
    create(args => hParser.value.parse(args) :: tParser.parse(args.tail))
  }
}
