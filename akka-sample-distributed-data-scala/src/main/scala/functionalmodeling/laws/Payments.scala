package functionalmodeling.laws

import java.time.OffsetDateTime

import Money._
import cats._
import cats.data._
import cats.implicits._

/**
  * https://debasishg.blogspot.in/2017/06/domain-models-early-abstractions-and.html
  */
object Payments extends Utils {
  case class Account(no: String, name: String, openDate: OffsetDateTime, closeDate: Option[OffsetDateTime] = None)
  case class Payment(account: Account, amount: Money, dateOfPayment: OffsetDateTime)

  def creditsOnly(p: Payment): Money = if (p.amount.isDebit) zeroMoney else p.amount

  def valuationConcrete(payments: List[Payment]) = payments.foldLeft(zeroMoney) { (a, e) => add(a, creditsOnly(e)) }

  def valuation(payments: List[Payment]): Money =  {
    implicit val m: Monoid[Money] = Money.MoneyAddMonoid
    mapReduce(payments)(creditsOnly)
  }

  def maxPayment(payments: List[Payment]): Money = {
    implicit val m: Monoid[Money] = Money.MoneyOrderMonoid
    mapReduce(payments)(creditsOnly)
  }
}
