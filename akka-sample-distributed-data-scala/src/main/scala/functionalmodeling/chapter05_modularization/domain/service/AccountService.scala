package functionalmodeling.chapter05_modularization
package domain
package service

import scalaz._

import java.util._

import repository.AccountRepository

sealed trait AccountType
case object Checking extends AccountType
case object Savings extends AccountType

trait AccountService[Account, Amount, Balance] {
  type AccountOperation[A] = Kleisli[Valid, AccountRepository, A]

  def open(no: String, name: String, rate: Option[BigDecimal], openingDate: Option[Date],
           accountType: AccountType): AccountOperation[Account]

  def close(no: String, closeDate: Option[Date]): AccountOperation[Account]

  def debit(no: String, amount: Amount): AccountOperation[Account]

  def credit(no: String, amount: Amount): AccountOperation[Account]

  def balance(no: String): AccountOperation[Balance]

  def transfer(from: String, to: String, amount: Amount): AccountOperation[(Account, Account)] = for {
    a <- debit(from, amount)
    b <- credit(to, amount)
  } yield ((a, b))
}
