package functionalmodeling.laws

import cats.kernel.laws.GroupLaws
import org.scalacheck.{Arbitrary, Gen}


class MoneySpec extends CatsSpec { def is = s2"""

  This is a specification for validating laws of Money

  (Money) should
    form a monoid under addtion $e1
    form a monoid under ordering $e2
  """
  
  import MoneyDateGen._

  def e1 = checkAll("Money", GroupLaws[Money].monoid(Money.MoneyAddMonoid))
  def e2 = checkAll("Money", GroupLaws[Money].monoid(Money.MoneyOrderMonoid))

}

object MoneyDateGen {
  implicit lazy val arbCurrency: Arbitrary[Currency] = Arbitrary { Gen.oneOf(AUD, USD, INR, JPY)}

  implicit def moneyArbitrary: Arbitrary[Money] = Arbitrary {
    for {
      i <- Arbitrary.arbitrary[Map[Currency, BigDecimal]]
    } yield new Money(i)
  }
}
