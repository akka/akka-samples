package shapeless_tut.parser

import org.scalatest.{FlatSpec, MustMatchers}

/**
  *
  */
class LabelledParserSpec extends FlatSpec with MustMatchers {
  "LabelledParser::apply" must "derive a parser for SimpleArguments" in {
    val args = List("--alpha", "a", "--beta", "1", "--charlie")
    val parsed = LabelledParser[SimpleArguments].parse(args)

    parsed must be (SimpleArguments("a", 1, true))

  }

  "LabelledParser::apply" must "derive a parser for SimpleArguments when lack boolean field" in {
    val args = List("--alpha", "a", "--beta", "1")
    val parsed = LabelledParser[SimpleArguments].parse(args)

    parsed must be (SimpleArguments("a", 1, false))

  }
}
