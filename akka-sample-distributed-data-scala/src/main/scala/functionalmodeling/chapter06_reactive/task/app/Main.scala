package functionalmodeling.chapter06_reactive
package task
package app

import model._
import common._

import repository.interpreter.AccountRepositoryInMemory
import service.interpreter.PortfolioService

import scalaz._
import Scalaz._

import scalaz.concurrent.Task
import Task._

object Main {
  def main(args: Array[String]): Unit = {
    import PortfolioService._

    val accountNo = "a-123"
    val asOf = today

    val ccyPF: Task[Seq[Balance]] = getCurrencyPortfolio(accountNo, asOf)(AccountRepositoryInMemory)
    val eqtPF: Task[Seq[Balance]] = getEquityPortfolio(accountNo, asOf)(AccountRepositoryInMemory)
    val fixPF: Task[Seq[Balance]] = getFixedIncomePortfolio(accountNo, asOf)(AccountRepositoryInMemory)

    val r = Task.gatherUnordered(Seq(ccyPF, eqtPF, fixPF))

    val portfolio = CustomerPortfolio(accountNo, asOf, r.unsafePerformSync.foldLeft(List.empty[Balance])(_ ++ _))

    println(portfolio)
  }
}
