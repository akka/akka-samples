package functionalmodeling.effects

import cats._
import cats.instances._

import scala.language.higherKinds

/**
  * https://debasishg.blogspot.in/2017/07/domain-models-late-evaluation-buys-you.html
  */
/*trait PaymentService[M[_]] {

  def paymentCycle: M[PaymentCycle]
  def qualifyingAccounts(paymentCycle: PaymentCycle): M[Vector[Account]]
  def payments(accounts: Vector[Account]): M[Vector[Payment]]
  def adjustTax(payments: Vector[Payment]): M[Vector[Payment]]
  def postToLedger(payments: Vector[Payment]): M[Unit]

  def processPayments()(implicit me: Monad[M]) = for {
    p <- paymentCycle
    a <- qualifyingAccounts(p)
    m <- payments(a)
    a <- adjustTax(m)
    _ <- postToLedger(a)
  } yield p
}*/
