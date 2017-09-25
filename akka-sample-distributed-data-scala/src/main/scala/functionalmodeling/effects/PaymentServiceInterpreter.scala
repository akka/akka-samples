package functionalmodeling.effects

import cats._
import cats.data._
import cats.implicits._

import Payments._

/**
  * https://github.com/debasishg/pigeon/blob/master/effects-dance/src/main/scala/effects/tagless/interpreters.scala
  */
/*class PaymentServiceInterpreter[M[_]](implicit me: MonadError[M, Throwable])
  extends PaymentService[M] with Utils {

  override def paymentCycle: M[PaymentCycle] = PaymentCycle(10, 2014).pure[M]

  override def qualifyingAccounts(paymentCycle: PaymentCycle): M[Vector[Account]] =
    Vector.empty[Account].pure[M]

  override def payments(accounts: Vector[Account]): M[Vector[Payment]] = Vector.empty[Payment].pure[M]

  override def adjustTax(payments: Vector[Payment]): M[Vector[Payment]] = payments.pure[M]

  override def postToLedger(payments: Vector[Payment]): M[Unit] = {
    val amountToPost = valuation(payments)
    // ... do the posting
    ().pure[M]
  }

  private def valuation(payments: Vector[Payment]): Money = {
    implicit val m: Monoid[Money] = Money.MoneyAddMonoid
    mapReduce(payments)(creditsOnly)
  }
}*/
