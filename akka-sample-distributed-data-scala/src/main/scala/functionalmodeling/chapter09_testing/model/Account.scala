package functionalmodeling.chapter09_testing
package model

import org.joda.time.DateTime

import scalaz._
import Scalaz._

object common {
  type Amount = BigDecimal

  def today = DateTime.now()
}

import common._
case class Balance(amount: Amount = 0)

sealed trait Account {
  def no: String
  def name: String
  def dateOfOpen: Option[DateTime]
  def dateOfClose: Option[DateTime]
  def balance: Balance
}

final case class CheckingAccount(
                                  no: String,
                                  name: String,
                                  dateOfOpen: Option[DateTime],
                                  dateOfClose: Option[DateTime] = None,
                                  balance: Balance = Balance()
                                ) extends Account
final case class SavingsAccount(
                                 no: String,
                                 name: String,
                                 rateOfInterest: Amount,
                                 dateOfOpen: Option[DateTime],
                                 dateOfClose: Option[DateTime] = None,
                                 balance: Balance = Balance()
                               ) extends Account

object Account {

  case class AccountException(cause: Throwable) extends Throwable(cause)

  case class InvalidAccountNo(message: String) extends Throwable(message)
  case class InvalidOpenCloseDate(message: String) extends Throwable(message)
  case class InvalidInterestRate(message: String) extends Throwable(message)
  case class AlreadyClosed(message: String) extends Throwable(message)
  case class InsufficientBalance(message: String) extends Throwable(message)
  
  private def validateAccountNo(no: String): ValidationNel[AccountException, String] =
    if (no.isEmpty || no.size < 5) AccountException(InvalidAccountNo(s"Account No has to be at least 5 characters long: found $no ")).failureNel[String]
    else no.successNel[AccountException]

  private def validateOpenCloseDate(od: DateTime, cd: Option[DateTime]) = cd.map { c =>
    if (c isBefore od) AccountException(InvalidOpenCloseDate(s"Close date [$c] cannot be earlier that open date [$od]")).failureNel[(Option[DateTime], Option[DateTime])]
    else (od.some, cd).successNel[AccountException]
  } getOrElse {(od.some, cd).successNel[AccountException]}

  private def validateRate(rate: BigDecimal) =
    if (rate <= BigDecimal(0)) AccountException(InvalidInterestRate(s"Interest rate $rate must be > 0")).failureNel[BigDecimal]
    else rate.successNel[AccountException]

  def checkingAccount(no: String, name: String, openDate: Option[DateTime], closeDate: Option[DateTime], balance: Balance
                     ): \/[NonEmptyList[AccountException], Account] = {
    val od = openDate.getOrElse(today)

    (
      validateAccountNo(no) |@|
      validateOpenCloseDate(od, closeDate)
      ) { (n, d) =>
      CheckingAccount(n, name, d._1, d._2, balance)
    }.disjunction
  }

  def savingsAccount(no: String, name: String, rate: BigDecimal, openDate: Option[DateTime],
                     closeDate: Option[DateTime], balance: Balance): \/[NonEmptyList[AccountException], Account] = {
    val od = openDate.getOrElse(today)

    (
     validateAccountNo(no) |@|
      validateOpenCloseDate(od, closeDate) |@|
      validateRate(rate)
    ) { (n, d, r) =>
      SavingsAccount(n, name, r, d._1, d._2, balance)
    }.disjunction
  }

  private def validateAccountAlreadyClosed(a: Account) = {
    if (a.dateOfClose isDefined) AccountException(AlreadyClosed(s"Account ${a.no} is already closed")).failureNel[Account]
    else a.successNel[AccountException]
  }

  private def validateCloseDate(a: Account, cd: DateTime) = {
    if (cd isBefore a.dateOfOpen.get) AccountException(InvalidOpenCloseDate(s"Close date [$cd] cannot be earlier than open date [${a.dateOfOpen.get}]")).failureNel[DateTime]
    else cd.successNel[AccountException]
  }

  def close(a: Account, closeDate: DateTime): \/[NonEmptyList[AccountException], Account] = {
    (
      validateAccountAlreadyClosed(a) |@|
      validateCloseDate(a, closeDate)
    ) {
      (acc, d) =>
        acc match {
          case c: CheckingAccount => c.copy(dateOfClose = Some(d))
          case s: SavingsAccount => s.copy(dateOfClose = Some(d))
        }

    }.disjunction
  }

  private def checkBalance(a: Account, amount: Amount) = {
    if (amount < 0 && a.balance.amount < -amount) AccountException(InsufficientBalance(s"Insufficient amount in ${a.no} to debit")).failureNel[Account]
    else a.successNel[AccountException]
  }

  def updateBalance(a: Account, amount: Amount): \/[NonEmptyList[AccountException], Account] = {
    (
      validateAccountAlreadyClosed(a) |@|
      checkBalance(a, amount)
    ) { (_, _) =>
      a match {
        case c: CheckingAccount => c.copy(balance = Balance(c.balance.amount + amount))
        case s: SavingsAccount => s.copy(balance = Balance(s.balance.amount + amount))
      }
    }.disjunction
  }

  def rate(a: Account) = a match {
    case SavingsAccount(_, _, r, _, _, _) => r.some
    case _ => None
  }
}

/*
import org.joda.time.DateTime
case class Account(no: String, name: String, idNo: String, dateOpened: DateTime, dateClosed: Option[DateTime])

trait IdVerifier {
  def verifyId(idNo: String, name: String): Boolean
}

trait AccountService {
  type Error = String
  type ErrorOr[A] =  \/[Error, A]

  def open(no: String, name: String, idNo: String, dateOpened: DateTime): IdVerifier => ErrorOr[Account] = iv => {
    if (iv.verifyId(idNo, name)) {
      Account(no, name, idNo, dateOpened, None).right
    } else s"Id ($idNo) validation failed".left
  }

}

case class MockIdVerifier() extends IdVerifier {
  override def verifyId(idNo: String, name: String) = true
}
*/
