package functionalmodeling.chapter01_introduction

import java.util._

import scala.util.{Failure, Success, Try}

/**
  * liaoshifu
  */
object Sample {

  type Amount = BigDecimal

  case class Balance(amount: Amount = 0)

  /*class Account(val no: String, val name: String, val dateOfOpening: Date, val balance: Balance = Balance()) {

    def debit(a: Amount) = {
      if (balance.amount < a)
        throw new Exception("Insufficient balance in account")
      new Account(no, name, dateOfOpening, Balance(balance.amount - a))
    }

    def credit(a: Amount) = {
      new Account(no, name, dateOfOpening, Balance(balance.amount + a))
    }
  }*/
  case class Account(vo: String, name: String, dateOfOpening: Date, balance: Balance = Balance())

  trait AccountService {
    def debit(a: Account, amount: Amount): Try[Account] = {
      if (a.balance.amount < amount)
        Failure(new Exception("Insufficient balance in account"))
      else Success(a.copy(balance = Balance(a.balance.amount - amount)))
    }

    def credit(a: Account, amount: Amount): Try[Account] = {
      Success(a.copy(balance = Balance(a.balance.amount + amount)))
    }
  }
}
