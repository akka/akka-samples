package functionalmodeling.chapter04_patterns
package patterns
package monoids.nomonoid

import common._
/**
  * TODO
  */
object NoMonoidApp {

  def main(args: Array[String]): Unit = {

    val accountNo1 = "accountNo1"
    val txns = List(
      Transaction("txid1", accountNo1, today, Money(Map(USD -> BigDecimal(1000), JPY -> BigDecimal(10000), AUD -> BigDecimal(5000))), CR, true),
      Transaction("txid2", accountNo1, today, Money(Map(USD -> BigDecimal(300), JPY -> BigDecimal(6000), AUD -> BigDecimal(1000))), DR, true),
      Transaction("txid3", accountNo1, today, Money(Map(USD -> BigDecimal(200), JPY -> BigDecimal(1000), AUD -> BigDecimal(1000))), DR, true),
      Transaction("txid4", accountNo1, today, Money(Map(USD -> BigDecimal(100))), CR, true),
      Transaction("txid5", accountNo1, today, Money(Map(JPY -> BigDecimal(1000), AUD -> BigDecimal(500))), CR, true),
      Transaction("txid6", accountNo1, today, Money(Map(AUD -> BigDecimal(500))), CR, true),
      Transaction("txid7", accountNo1, today, Money(Map(USD -> BigDecimal(2000), JPY -> BigDecimal(20000), AUD -> BigDecimal(3000))), DR, true),
      Transaction("txid8", accountNo1, today, Money(Map(JPY -> BigDecimal(5000), AUD -> BigDecimal(1000))), CR, true),
      Transaction("txid9", accountNo1, today, Money(Map(USD -> BigDecimal(8000), AUD -> BigDecimal(500))), CR, true)

    )

    val maxDebitOnDay = Analytics.maxDebitOnDay(txns)
    println(s"The max debit is: $maxDebitOnDay")

    val sumBalances = Analytics.sumBalances(txns.map(tx => Balance(tx.amount)))
    println(s"The balance is: $sumBalances")
  }
}
