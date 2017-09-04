package functionalmodeling.chapter06_reactive
package domain
package service
package interpreter

import java.util._

import scalaz._
import Scalaz._
import Kleisli._

import model._
import common._
import repository.AccountRepository

import scala.concurrent._
import ExecutionContext.Implicits.global

class AccountServiceInterpreter extends AccountService[Account, Amount, Balance] {

  override def open(no: String, name: String, rate: Option[BigDecimal], openingDate: Option[Date],
                    accountType: AccountType) = kleisli[Valid, AccountRepository, Account] { repo =>
    EitherT {
      Future {
        repo.query(no) match {
          case \/-(Some(a)) => NonEmptyList(s"Already existing account with no $no").left[Account]
          case \/-(None) => accountType match {
            case Checking => Account.checkingAccount(no, name, openingDate, None, Balance()).flatMap(repo.store)
            case Savings => rate map { r =>
              Account.savingsAccount(no, name, r, openingDate, None, Balance()).flatMap(repo.store)
            } getOrElse {
              NonEmptyList(s"Rate needs to be given for savings account").left[Account]
            }
          }

          case a @ -\/(_) => a
        }
      }
    }
  }

  override def close(no: String, closeDate: Option[Date]) = kleisli[Valid, AccountRepository, Account] { repo =>
    addEitherTFuture { repo.query(no) match {
      case \/-(None) => NonEmptyList(s"Account $no does exist").left[Account]
      case \/-(Some(a)) =>
        val cd = closeDate.getOrElse(today)
        Account.close(a, cd).flatMap(repo.store)
      case a @ -\/(_) => a
    } }
  }

  override def debit(no: String, amount: Amount) = up(no, amount, D)

  override def credit(no: String, amount: Amount) = up(no, amount, C)

  override def balance(no: String) = kleisli[Valid, AccountRepository, Balance] { repo =>
    EitherT { Future { repo.balance(no) } }
  }

  private trait DC
  private case object D extends DC
  private case object C extends DC

  private def up(no: String, amount: Amount, dc: DC): AccountOperation[Account] =
    kleisli[Valid, AccountRepository, Account] { repo =>
      EitherT {
        Future {
          repo.query(no) match {
            case \/-(Some(a)) => dc match {
              case D => Account.updateBalance(a, -amount).flatMap(repo.store)
              case C => Account.updateBalance(a, amount).flatMap(repo.store)
            }
            case \/-(None) => NonEmptyList(s"Account $no does not exist").left[Account]
            case a @ -\/(_) => a
          }
        }
      }
  }
}

object AccountService extends AccountServiceInterpreter
