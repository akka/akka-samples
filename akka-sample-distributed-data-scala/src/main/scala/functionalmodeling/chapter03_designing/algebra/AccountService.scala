package functionalmodeling.chapter03_designing.algebra

import java.util.Date

import scala.util.Try

/**
  *
  */
trait AccountService[Account, Amount, Balance] {
  def open(no: String, name: String, openingDate: Option[Date]): Try[Account]
  def close(account: Account, closeDate: Option[Date]): Try[Account]
  def debit(account: Account, amount: Amount): Try[Account]
  def credit(account: Account, amount: Amount): Try[Account]
  def balance(account: Account): Try[Balance]

  def transfer(from: Account, to: Account, amount: Amount): Try[(Account, Account, Amount)] = for {
    a <- debit(from, amount)
    b <- credit(to, amount)
  } yield (a, b, amount)
}
