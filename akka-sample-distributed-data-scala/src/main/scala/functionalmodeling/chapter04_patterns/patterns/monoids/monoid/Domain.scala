package functionalmodeling.chapter04_patterns
package patterns
package monoids
package monoid

import java.util.Date

sealed trait TransactionType
case object DR extends TransactionType
case object CR extends TransactionType

sealed trait Currency
case object USD extends Currency
case object JPY extends Currency
case object AUD extends Currency
case object INR extends Currency

object common {
  type Amount = BigDecimal

  def today = new Date
}

case class Money(m: Map[Currency, BigDecimal]) {
  def toBaseCurrency: BigDecimal = m.getOrElse(USD, BigDecimal(0))
}

case class Transaction(txid: String, accountNo: String, date: Date,
                       amount: Money, txnType: TransactionType, status: Boolean)

case class Balance(b: Money)

trait Analytics[Transaction, Balance, Money] {
  def maxDebitOnDay(txns: List[Transaction])(implicit m: Monoid[Money]): Money
  def sumBalances(bs: List[Balance])(implicit m: Monoid[Money]): Money
}

object Analytics extends Analytics[Transaction, Balance, Money] {

  import Monoid._

  final val zeroMoney: Money = Money(Monoid[Map[Currency, BigDecimal]].zero)

  final val baseCurrency = USD

  private def valueOf(txn: Transaction): Money = {
    if (txn.status) txn.amount
    else MoneyAdditionMonoid.op(txn.amount, Money(Map(baseCurrency -> BigDecimal(100))))
  }

  private def creditBalance(bal: Balance): Money = {
    if (bal.b.toBaseCurrency > 0) bal.b else zeroMoney
  }
  
  override def maxDebitOnDay(txns: List[Transaction])(implicit m: Monoid[Money]) = {
    txns.filter(_.txnType == DR).foldLeft(m.zero) { (a, txn) =>
      m.op(a, valueOf(txn))
    }
  }

  override def sumBalances(bs: List[Balance])(implicit m: Monoid[Money]) =
    bs.foldLeft(m.zero) { (a, bal) => m.op(a, creditBalance(bal)) }
}
