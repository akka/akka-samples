package functionalmodeling.chapter06_reactive
package domain
package model

import java.util.{ Calendar, Date }

import scalaz._
import Scalaz._

object common {
  type Amount = BigDecimal

  def today = Calendar.getInstance().getTime
}

import common._

case class Balance(amount: Amount = 0)

sealed trait Account {
  def no: String
  def name: String
  def dateOfOpen: Option[Date]
  def dateOfClose: Option[Date]
  def balance: Balance
}

final case class CheckingAccount(
                                no: String,
                                name: String,
                                dateOfOpen: Option[Date],
                                dateOfClose: Option[Date] = None,
                                balance: Balance = Balance()
                                ) extends Account
final case class SavingsAccount(
                               no: String,
                               name: String,
                               rateOfInterest: Amount,
                               dateOfOpen: Option[Date],
                               dateOfClose: Option[Date] = None,
                               balance: Balance = Balance()
                               ) extends Account

object Account {
  private def validateAccountNo(no: String) =
    if (no.isEmpty || no.size < 5) s"Account No has to be at least 5 characters long: found $no ".failureNel[String]
    else no.successNel[String]

  private def validateOpenCloseDate(od: Date, cd: Option[Date]) = cd.map { c =>
    if (c before od) s"Close date [$c] cannot be earlier that open date [$od]".failureNel[(Option[Date], Option[Date])]
    else (od.some, cd).successNel[String]
  } getOrElse {(od.some, cd).successNel[String]}

  private def validateRate(rate: BigDecimal) =
    if (rate <= BigDecimal(0)) s"Interest rate $rate must be > 0".failureNel[BigDecimal]
    else rate.successNel[String]

  def checkingAccount(no: String, name: String, opendDate: Option[Date], closeDate: Option[Date], balance: Balance
                     ): \/[NonEmptyList[String], Account] = {
    val od = opendDate.getOrElse(today)

    (
      validateAccountNo(no) |@|
      validateOpenCloseDate(od, closeDate)
      ) { (n, d) =>
      CheckingAccount(n, name, d._1, d._2, balance)
    }.disjunction
  }

  def savingsAccount(no: String, name: String, rate: BigDecimal, openDate: Option[Date],
                     closeDate: Option[Date], balance: Balance): \/[NonEmptyList[String], Account] = {
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
    if (a.dateOfClose isDefined) s"Account ${a.no} is already closed".failureNel[Account]
    else a.successNel[String]
  }

  private def validateCloseDate(a: Account, cd: Date) = {
    if (cd before a.dateOfOpen.get) s"Close date [$cd] cannot be earlier than open date [${a.dateOfOpen.get}]".failureNel[Date]
    else cd.successNel[String]
  }

  def close(a: Account, closeDate: Date): \/[NonEmptyList[String], Account] = {
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
    if (amount < 0 && a.balance.amount < -amount) s"Insufficient amount in ${a.no} to debit".failureNel[Account]
    else a.successNel[String]
  }

  def updateBalance(a: Account, amount: Amount): \/[NonEmptyList[String], Account] = {
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