package functionalmodeling.laws

import java.time.OffsetDateTime

import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalacheck.Arbitrary.arbitrary

import cats._
import kernel.laws.GroupLaws

import Payments._
import Money._

class PaymentsSpec extends CatsSpec {
  def is =
    s2"""
        This is a specification for validating Payments

        (Payments) should
          be valuated properly $e1
          be valuated properly $e2
          be ordered  properly $e3
      """

  import DataGen._

  def e1 = Prop.forAll(Gen.listOfN(10, PaymentGen)) { payments =>
    valuation(payments) != Money.zeroMoney }

  def e2 = Prop.forAll(Gen.listOfN(10, PaymentGen)) { payments =>
    valuation(payments) === valuationConcrete(payments)
  }

  def e3 = Prop.forAll(Gen.listOfN(10, NonZeroPaymentGen) suchThat (_.nonEmpty)) { payments =>
    maxPayment(payments).toBaseCurrency === payments.map(creditsOnly(_).toBaseCurrency).max
  }
}

object DataGen extends Java8Arbitrary {
  import Payments._

  implicit lazy val arbCurreny: Arbitrary[Currency] = Arbitrary { Gen.oneOf(AUD, USD, INR, JPY)}

  implicit def moneyArbitray: Arbitrary[Money] = Arbitrary(
    for {
      i <- Arbitrary.arbitrary[Map[Currency, BigDecimal]]
    } yield new Money(i)
  )

  val genValidAccountNo = Gen.choose(100000, 999999).map(_.toString)
  val genName = Gen.oneOf("john", "david", "mary")

  val validAccountGen = for {
    n <- genValidAccountNo
    m <- genName
    d <- arbitrary[OffsetDateTime]
  } yield Account(n, m, d)

  val PaymentGen = for {
    a <- validAccountGen
    m <- Arbitrary.arbitrary[Money]
    d <- Arbitrary.arbitrary[OffsetDateTime]
  } yield Payment(a, m, d)

  val NonZeroPaymentGen = for {
    a <- validAccountGen
    m <- Arbitrary.arbitrary[Money] suchThat (x => x.items.nonEmpty)
    d <- Arbitrary.arbitrary[OffsetDateTime]
  } yield Payment(a, m, d)
}