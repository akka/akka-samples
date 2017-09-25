package functionalmodeling.laws

import cats.{Eq, Monoid, Order}

import cats.implicits._

sealed trait Currency
  case object USD extends Currency
  case object AUD extends Currency
  case object JPY extends Currency
  case object INR extends Currency

  class Money private [functionalmodeling] (val items: Map[Currency, BigDecimal]) {
    def toBaseCurrency: BigDecimal =
      items.foldLeft(BigDecimal(0)) { case (a, (ccy, amount)) =>
        a + Money.exchangeRateWithUSD.get(ccy).getOrElse(BigDecimal(1)) * amount
      }

    def isDebit = toBaseCurrency < 0

    def +(m: Money) = new Money(items |+| m.items)
    override def toString: String = items.toList.mkString(",")
  }

  object Money {
    def apply(amount: BigDecimal, ccy: Currency) = new Money(Map(ccy -> amount))

    def add(m: Money, amount: BigDecimal, ccy: Currency) = m.items.get(ccy) match {
      case Some(a) => new Money(m.items + (ccy -> (a + amount)))
      case None => new Money(m.items + (ccy -> amount))
    }

    def add(m: Money, n: Money) = new Money(m.items |+| n.items)

    def addSimple(m: Money, n: Money) = new Money(
      (m.items.toList ++ n.items.toList)
        .groupBy(_._1)
        .map { case (k, v) =>
          (k, v.map(_._2).sum)
        }
    )

    final val zeroMoney = new Money(Map.empty[Currency, BigDecimal])

    final val exchangeRateWithUSD: Map[Currency, BigDecimal] =
      Map(AUD -> 0.76, JPY -> 0.009, INR -> 0.016, USD -> 1.0)

    val MoneyAddMonoid: Monoid[Money] = new Monoid[Money] {
      override def combine(x: Money, y: Money): Money = add(x, y)

      override def empty: Money = zeroMoney
    }

    implicit val MoneyEq: Eq[Money] = new Eq[Money] {
      override def eqv(x: Money, y: Money): Boolean = x.items === y.items
    }

    val MoneyOrder: Order[Money] = new Order[Money] {
      override def compare(x: Money, y: Money): Int = {
        val mbase = x.toBaseCurrency
        val nbase = y.toBaseCurrency
        if (mbase > nbase) 1
        else if (mbase < nbase) -1
        else 0
      }
    }

    val MoneyOrderMonoid: Monoid[Money] = new Monoid[Money] {
      override def combine(x: Money, y: Money): Money =
        if (x === zeroMoney) y
        else if (y === zeroMoney) x
        else if (MoneyOrder.compare(x, y) >= 0) x else y

      override def empty: Money = zeroMoney
    }
  }
