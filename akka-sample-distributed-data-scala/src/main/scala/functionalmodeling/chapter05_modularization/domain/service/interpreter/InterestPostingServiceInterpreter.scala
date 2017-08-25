package functionalmodeling.chapter05_modularization
package domain
package service
package interpreter

import model.Account
import model.common.Amount

import scalaz._
import Scalaz._
import Kleisli._

class InterestPostingServiceInterpreter extends InterestPostingService[Account, Amount]{
  override def computeInterest = kleisli[Valid, Account, Amount] { a =>
    if (a.dateOfClose isDefined) NonEmptyList(s"Account ${a.no} is closed").left
    else Account.rate(a).map { r =>
      val aa = a.balance.amount
      aa + aa * r
    }.getOrElse(BigDecimal(0)).right
  }

  override def computeTax = kleisli[Valid, Amount, Amount] { amount =>
    (amount * 0.1).right
  }
}

object InterestPostingService extends InterestPostingServiceInterpreter
