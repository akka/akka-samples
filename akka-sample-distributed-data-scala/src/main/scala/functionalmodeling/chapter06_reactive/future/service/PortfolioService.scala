package functionalmodeling.chapter06_reactive
package future
package service

import scalaz._
import Scalaz._

import model._
import repository._

import scala.concurrent._
import ExecutionContext.Implicits.global

import java.util._

trait PortfolioService {
  type PFOperation[A] = Kleisli[Future, AccountRepository, Seq[A]]

  def getCurrencyPortfolio(no: String, asOf: Date): PFOperation[Balance]
  def getEquityPortfolio(no: String, asOf: Date): PFOperation[Balance]
  def getFixedIncomePortfolio(no: String, asOf: Date): PFOperation[Balance]
}
