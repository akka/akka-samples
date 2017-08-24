package functionalmodeling.chapter04_patterns.patterns

/**
  *
  */

import Monoid._
import functionalmodeling.chapter04_patterns.patterns.States.Transaction

import scalaz.State
import State._

object States {
  type AccountNo = String
  type BS = Map[AccountNo, Balance]



  def updateBalance(txns: List[Transaction]) = modify { (b: BS) =>
    txns.foldLeft(b) { (a, txn) =>
      implicitly[Monoid[BS]].op(a, Map(txn.accountNo -> Balance(txn.amount)))
    }
  }

  case class Transaction(accountNo: AccountNo, amount: Money)



}

object StatesApp {
  def main(args: Array[String]): Unit = {
    import States._

    val balances: BS = Map(
      "a1" -> Balance(),
      "a2" -> Balance(),
      "a3" -> Balance(),
      "a4" -> Balance(),
      "a5" -> Balance()
    )

    val txns: List[Transaction] = List(
      Transaction("a1", Money(Map(USD -> BigDecimal(100)))),
      Transaction("a2", Money(Map(USD -> BigDecimal(100)))),
      Transaction("a1", Money(Map(INR -> BigDecimal(500000)))),
      Transaction("a3", Money(Map(USD -> BigDecimal(100)))),
      Transaction("a2", Money(Map(AUD -> BigDecimal(200))))
    )

    println(updateBalance(txns) run balances)
  }
}
