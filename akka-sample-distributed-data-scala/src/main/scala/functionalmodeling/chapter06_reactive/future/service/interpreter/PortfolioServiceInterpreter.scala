package functionalmodeling.chapter06_reactive
package future
package service
package interpreter

import java.util.Date

import model._
import common._

import repository.AccountRepository

import scalaz._
import Scalaz._
import Kleisli._

import scala.concurrent._
import ExecutionContext.Implicits.global

class PortfolioServiceInterpreter extends PortfolioService {
  override def getCurrencyPortfolio(no: String, asOf: Date): PFOperation[Balance] = kleisli[Future, AccountRepository, Seq[Balance]] { repo =>
    Future {
      repo.getCurrencyBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch currency balance")
      }
    }
  }

  override def getEquityPortfolio(no: String, asOf: Date) = kleisli[Future, AccountRepository, Seq[Balance]] {repo =>
    Future {
      repo.getEquityBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch equity balance")
      }
    }
  }

  override def getFixedIncomePortfolio(no: String, asOf: Date) = kleisli[Future, AccountRepository, Seq[Balance]] { repo =>
    Future {
      repo.getFixedIncomeBalance(no, asOf) match {
        case \/-(b) => b
        case -\/(_) => throw new Exception(s"Failed to fetch fixed income balance")
      }
    }
  }
}

object PortfolioService extends PortfolioServiceInterpreter