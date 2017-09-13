package functionalmodeling
package chapter09_testing
package model

import org.scalacheck._
import Prop.{BooleanOperators, forAll}
import Gen._
import Arbitrary.arbitrary
import org.joda.time.{DateTime, DateTimeZone}

import scalaz._
import Scalaz._
import java.util.Date

object AllGen {

  import common._

  val genAmount = for {
    value <- Gen.choose(100, 10000000)
    valueDecimal <- BigDecimal.valueOf(value)
  } yield valueDecimal / 100

  val genBalance = genAmount map Balance

  implicit val arbitraryBalance: Arbitrary[Balance] = Arbitrary { genBalance }

  implicit val arbitraryDateTime: Arbitrary[DateTime] = Arbitrary {
    for {
      d <- arbitrary[Date]
      year <- choose(0, 9999)
    } yield (new DateTime(d, DateTimeZone.UTC)).withYear(year)
  }

  val genValidAccountNo = Gen.choose(100000, 999999).map(_.toString)

  val genName = Gen.oneOf("jonh", "david", "mary")

  def genOptionalValidCloseDate(seed: DateTime) =
    Gen.frequency(
      (8, Some(aDateAfter(seed))),
      (1, None)
    )

  def aDateAfter(date: DateTime) = date.plusMillis(10000)
  def aDateBefore(date: DateTime) = date.minusMillis(10000)

  def genInvalidOptionalCloseDate(seed: DateTime) = Gen.oneOf(Some(aDateBefore(seed)), None)


}

object CheckingAccountSpecification extends Properties("Account") {

  import AllGen._
  import Account._

  val validCheckingAccountGen = for {
    no <- genValidAccountNo
    nm <- genName
    od <- arbitrary[DateTime]
    cd <- genOptionalValidCloseDate(od)
    bl <- arbitrary[Balance]
  } yield checkingAccount(no, nm, Some(od), cd, bl)

  val validClosedCheckingAccountGen = for {
    no <- genValidAccountNo
    nm <- genName
    od <- arbitrary[DateTime]
    cd <- genOptionalValidCloseDate(od) suchThat (_ isDefined)
    bl <- arbitrary[Balance]
  } yield checkingAccount(no, nm, Some(od), cd, bl)

  val validZeroBalanceCheckingAccountGen = for {
    no <- genValidAccountNo
    nm <- genName
    od <- arbitrary[DateTime]
  } yield checkingAccount(no, nm, Some(od), None, Balance())

  property("Close Account if not already closed") =
    forAll(validCheckingAccountGen) { ga =>
      ga.map { account =>
        account.dateOfClose.map(_ => true).getOrElse(
          close(account, account.dateOfOpen.map(aDateAfter(_)).getOrElse(common.today))
            .isRight == true
        )
      }.getOrElse(false)
    }

  property("Update balance on closed account fails") =
    forAll(validClosedCheckingAccountGen, genAmount) { (creation, amount) =>
      creation.map { account =>
        updateBalance(account, amount) match {
          case -\/(NonEmptyList(AccountException(AlreadyClosed(_)), INil())) => true
          case _ => false
        }
      }.getOrElse(false)
    }

  property("Upate balance on account with insufficient founds fails") = forAll(
    validZeroBalanceCheckingAccountGen, genAmount
  ) { (creation, amount) =>
    creation.map { account =>
      updateBalance(account, -amount) match {
        case -\/(NonEmptyList(AccountException(InsufficientBalance(_)), INil())) => true
        case _ => false

      } 
    }.getOrElse(false)
  }
}