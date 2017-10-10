package shapeless_tut.parser

import org.scalatest.{FlatSpec, MustMatchers}

/**
  * 2017-10-03
  */
case class SimpleArguments(alpha: String, beta: Int, charlie: Boolean)

class ParserSpec extends FlatSpec with MustMatchers {
  "Parser::apply" must "derive a parser for SimpleArguments" in {
    val args = List("abc dads", "12", "true")
    val parsed = Parser[SimpleArguments].parse(args)
    parsed must be (SimpleArguments("abc dads", 12, true))
  }
}
