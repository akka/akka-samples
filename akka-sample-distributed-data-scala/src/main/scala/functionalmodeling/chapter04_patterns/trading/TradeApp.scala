package functionalmodeling.chapter04_patterns
package trading

/**
  *
  */
object TradeApp {

  def main(args: Array[String]): Unit = {

    import TradeModel._

    val cos = List(Map(
      "instrument" -> "ins1/10/100-ins2/20/200",
      "no" -> "no123",
      "customer" -> "customer123"
    ), Map(
      "instrument" -> "ins3/30/30-ins4/40/400",
      "no" -> "no456",
      "customer" -> "customer456"
    )
    )
    
    val market = Singapore
    val broker = "broker1"
    val cs = List("client1", "client2")
    val r = TradingInterpreter.tradeGeneration(market, broker, cs).run(cos)
    println(r)
  }
}
