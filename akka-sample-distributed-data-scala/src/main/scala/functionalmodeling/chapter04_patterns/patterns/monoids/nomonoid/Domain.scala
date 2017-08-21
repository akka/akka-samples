package functionalmodeling.chapter04_patterns
package patterns
package monoids
package nomonoid

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

  def +(that: Money) = {
    val n = that.m.foldLeft(m) { (a, e) =>
      val (ccy, amt) = e
      a.get(ccy).map { amount =>
        a + ((ccy, amt + amount))
      }.getOrElse(a + ((ccy, amt)))
    }

    Money(n)
  }

  def toBaseCurrency: BigDecimal = m.getOrElse(USD, BigDecimal(0))
}

object Money {
  val zeroMoney = Money(Map.empty[Currency, BigDecimal])
}

import Money._
object MoneyOrdering extends Ordering[Money] {
  override def compare(x: Money, y: Money) = x.toBaseCurrency.compare(y.toBaseCurrency)
}

import MoneyOrdering._
import scala.math.Ordering

case class Transaction(txid: String, accountNo: String, date: Date,
                       amount: Money, txnType: TransactionType, status: Boolean)

case class Balance(b: Money)

trait Analytics[Transaction, Balance, Money] {
  def maxDebitOnDay(txns: List[Transaction]): Money
  def sumBalances(bs: List[Balance]): Money
}

object Analytics extends Analytics[Transaction, Balance, Money] {
  override def maxDebitOnDay(txns: List[Transaction]) = {
    txns.filter(_.txnType == DR).foldLeft(zeroMoney) { (a, txn) =>
      if (gt(txn.amount, a)) txn.amount else a
    }
  }

  override def sumBalances(bs: List[Balance]) = bs.foldLeft(zeroMoney)(_ + _.b)
}