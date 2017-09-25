package functionalmodeling.effects

import cats.Applicative
import cats.data.WriterT
import cats.implicits._

/**
  *
  */
/*final class AuditablePaymentService[M[_]: Applicative, A](paymentService: PaymentService[M])
  extends PaymentService[WriterT[M, Vector[String], A]] {

  def paymentCycle: WriterT[M, Vector[String], PaymentCycle] =
    WriterT.lift(paymentService.paymentCycle)

  override def qualifyingAccounts(paymentCycle: PaymentCycle): WriterT[M, Vector[String], Vector[Account]] =
    WriterT.lift(paymentService.qualifyingAccounts(paymentCycle))

  def payments(accounts: Vector[Account]): WriterT[M, Vector[String], Vector[Payment]] =
    WriterT.putT(paymentService.payments(accounts))(accounts.map(_.no))

  override def adjustTax(payments: Vector[Payment]): WriterT[M, Vector[String], Vector[Payment]] =
    WriterT.putT(paymentService.adjustTax(payments))(payments.map(_.toString))

  override def postToLedger(payments: Vector[Payment]): WriterT[M, Vector[String], Unit] =
    WriterT.lift(paymentService.postToLedger(payments))
}*/
