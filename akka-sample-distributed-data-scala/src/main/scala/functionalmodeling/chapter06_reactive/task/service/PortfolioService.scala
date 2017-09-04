package functionalmodeling.chapter06_reactive
package task
package service

import scalaz._
import Scalaz._
import model._
import repository._
import java.util._

import scalaz.concurrent.Task

trait PortfolioService {
  type PFOperation[A] = Kleisli[Task, AccountRepository, Seq[A]]

  def getCurrencyPortfolio(no: String, asOf: Date): PFOperation[Balance]
  def getEquityPortfolio(no: String, asOf: Date): PFOperation[Balance]
  def getFixedIncomePortfolio(no: String, asOf: Date): PFOperation[Balance]
}
