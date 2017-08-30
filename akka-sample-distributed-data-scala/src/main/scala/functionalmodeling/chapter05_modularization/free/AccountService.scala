package functionalmodeling.chapter05_modularization
package free

import java.util.Date

import scalaz._
import Scalaz._

import common._

/**
  * Free service
  */
trait AccountService[Account, Amount, Balance] {
  def open(no: String, name: String, openingDate: Option[Date]): AccountRepo[Account]
  def close(no: String, closeDate: Option[Date]): AccountRepo[Account]
  def debit(no: String, amount: Amount): AccountRepo[Account]
  def credit(no: String, amount: Amount): AccountRepo[Account]
  def balance(no: String): AccountRepo[Balance]
}

object AccountService extends AccountService[Account, Amount, Balance] with AccountRepository {

  override def open(no: String, name: String, openingDate: Option[Date]) = for {
    _ <- store(Account(no, name, openingDate.get))
    a <- query(no)
  } yield a

  private val close: Option[Date] => Account => Account = cd => a => {
    val d = cd match {
      case c @ Some(_) => c
      case None => Some(today)
    }
    a.copy(dateOfClosing = d)
  }

  override def close(no: String, closeDate: Option[Date]) = for {
    _ <- update(no, close(closeDate))
    a <- query(no)
  } yield a

  private def debitImpl(a: Account, amount: Amount) = {
    if (a.balance.amount < amount) throw new RuntimeException("insufficient fund to debit")
    a.copy(balance = Balance(a.balance.amount - amount))
  }

  override def debit(no: String, amount: Amount) = for {
    _ <- updateBalance(no, amount, debitImpl)
    a <- query(no)
  } yield a

  private def creditImpl(a: Account, amount: Amount) = a.copy(balance = Balance(a.balance.amount + amount))
  
  override def credit(no: String, amount: Amount) = for {
    _ <- updateBalance(no, amount, creditImpl)
    a <- query(no)
  } yield a

  override def balance(no: String): AccountRepo[Balance] = for {
    a <- query(no)
  } yield a.balance
}