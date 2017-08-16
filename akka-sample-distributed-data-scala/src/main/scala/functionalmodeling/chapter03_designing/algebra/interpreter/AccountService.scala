package functionalmodeling.chapter03_designing.algebra.interpreter

import functionalmodeling.chapter03_designing.algebra.AccountService

import common._

import java.util._
import scala.util._


object AccountService extends AccountService[Account, Amount, Balance] {
  def open(no: String, name: String, openingDate: Option[Date]): Try[Account] = {
    if (no.isEmpty || name.isEmpty) Failure(new Exception(s"Account no or name cannot be blank") )
    else if (openingDate.getOrElse(today) before today) Failure(new Exception(s"Cannot open account in the past"))
    else Success(Account(no, name, openingDate.getOrElse(today)))
  }

  def close(account: Account, closeDate: Option[Date]): Try[Account] = {
    val cd = closeDate.getOrElse(today)
    if (cd before account.dateOfOpening)
      Failure(new Exception(s"Close date $cd cannot be before opening date ${account.dateOfOpening}"))
    else Success(account.copy(dateOfClosing = Some(cd)))
  }

  def debit(a: Account, amount: Amount): Try[Account] = {
    if (a.balance.amount < amount) Failure(new Exception("Insufficient balance"))
    else Success(a.copy(balance = Balance(a.balance.amount - amount)))
  }

  def credit(a: Account, amount: Amount): Try[Account] =
    Success(a.copy(balance = Balance(a.balance.amount + amount)))

  def balance(account: Account): Try[Balance] = Success(account.balance)
}
