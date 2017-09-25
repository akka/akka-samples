package functionalmodeling.effects

import cats._
import cats.data._
import cats.implicits._

sealed trait Currency
case object USD extends Currency
case object AUD extends Currency
case object JPY extends Currency
case object INR extends Currency

class Money private[effects] (val items: Map[Currency, BigDecimal]) {
  def toBaseCurrency: BigDecimal = items.foldLeft(BigDecimal(0)) { case (a, (ccy, amount)) =>
    a + Money.exchangeRateWithUSD.get(ccy).getOrElse(BigDecimal(1)) * amount
  }

  def isDebit = toBaseCurrency < 0
  def +(m: Money) = new Money(items |+| m.items)

  override def toString = items.toList.mkString(",")
}

object Money {
  final val zeroMoney = new Money(Map.empty[Currency, BigDecimal])

  // smart constructor
  def apply(amount: BigDecimal, ccy: Currency) = new Money(Map(ccy -> amount))

  // uses the algebraic properties of Money
  def add(m: Money, amount: BigDecimal, ccy: Currency) = new Money(m.items |+| Map(ccy -> amount))
  def add(m: Money, n: Money) = new Money(m.items |+| n.items)

  // concrete naive implementation: don't
  def addSimple(m: Money, n: Money) = new Money(
    (m.items.toList ++ n.items.toList)
      .groupBy(_._1)
      .map { case (k, v) =>
        (k, v.map(_._2).sum)
      }
  )

  final val exchangeRateWithUSD: Map[Currency, BigDecimal] =
    Map(AUD -> 0.76, JPY -> 0.009, INR -> 0.016, USD -> 1.0)

  val MoneyAddMonoid: Monoid[Money] = new Monoid[Money] {
    def combine(m: Money, n: Money): Money = add(m, n)
    def empty: Money = zeroMoney
  }

  implicit val MoneyEq: Eq[Money] = new Eq[Money] {
    def eqv(m: Money, n: Money): Boolean = m.items === n.items
  }

  val MoneyOrder: Order[Money] = new Order[Money] {
    def compare(m: Money, n: Money) = {
      val mbase = m.toBaseCurrency
      val nbase = n.toBaseCurrency
      if (mbase > nbase) 1
      else if (mbase < nbase) -1
      else 0
    }
  }

  val MoneyOrderMonoid: Monoid[Money] = new Monoid[Money] {
    def combine(m: Money, n: Money): Money =
      if (m === zeroMoney) n
      else if (n === zeroMoney) m
      else if (MoneyOrder.compare(m, n) >= 0) m else n

    def empty: Money = zeroMoney
  }
}