package functionalmodeling.chapter06_reactive
package task
package service
package interpreter

import java.util.Date

import model._
import common._

import repository.AccountRepository

import scalaz._
import Scalaz._
import Kleisli._

import scalaz.concurrent.Task

class PortfolioServiceInterpreter extends PortfolioService {
  override def getCurrencyPortfolio(no: String, asOf: Date): PFOperation[Balance] = kleisli[Task, AccountRepository, Seq[Balance]] { repo =>
    Task {
      repo.getCurrencyBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch currency balance")
      }
    }
  }

  override def getEquityPortfolio(no: String, asOf: Date) = kleisli[Task, AccountRepository, Seq[Balance]] {repo =>
    Task {
      repo.getEquityBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch equity balance")
      }
    }
  }

  override def getFixedIncomePortfolio(no: String, asOf: Date) = kleisli[Task, AccountRepository, Seq[Balance]] { repo =>
    Task {
      repo.getFixedIncomeBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch fixed income balance")
      }
    }
  }
}

object PortfolioService extends PortfolioServiceInterpreter