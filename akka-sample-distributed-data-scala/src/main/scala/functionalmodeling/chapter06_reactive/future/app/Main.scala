package functionalmodeling.chapter06_reactive
package future
package app

import model._
import common._

import repository.interpreter.AccountRepositoryInMemory
import service.interpreter.PortfolioService

import scalaz._
import Scalaz._

import scala.concurrent._
import ExecutionContext.Implicits.global
import duration._

object Main {
  def main(args: Array[String]): Unit = {
    import PortfolioService._

    val accountNo = "a-123"
    val asOf = today

    val ccyPF: Future[Seq[Balance]] = getCurrencyPortfolio(accountNo, asOf)(AccountRepositoryInMemory)
    val eqtPF: Future[Seq[Balance]] = getEquityPortfolio(accountNo, asOf)(AccountRepositoryInMemory)
    val fixPF: Future[Seq[Balance]] = getFixedIncomePortfolio(accountNo, asOf)(AccountRepositoryInMemory)

    val portfolio: Future[Portfolio] = for {
      c <- ccyPF
      e <- eqtPF
      f <- fixPF
    } yield CustomerPortfolio(accountNo, asOf, c ++ e ++ f)

    val y = Await.result(portfolio, 30.seconds)

    println(y)
  }
}
