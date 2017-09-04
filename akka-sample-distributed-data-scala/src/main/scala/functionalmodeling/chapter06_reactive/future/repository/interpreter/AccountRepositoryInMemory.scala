package functionalmodeling.chapter06_reactive
package future
package repository
package interpreter

import java.util.Date

import scala.collection.mutable.{Map => MMap}
import scalaz._
import Scalaz._

import model._

trait AccountRepositoryInMemory extends AccountRepository {
  lazy val repo = MMap.empty[String, Account]

  lazy val ccyBalanceRepo = MMap.empty[(String, Date), Seq[Balance]]
  lazy val equityBalanceRepo = MMap.empty[(String, Date), Seq[Balance]]
  lazy val fixedIncomeBalanceRepo = MMap.empty[(String, Date), Seq[Balance]]

  override def query(no: String) = repo.get(no).right

  override def store(a: Account) = {
    repo += ((a.no, a))
    a.right
  }

  override def query(openedON: Date) = repo.values.filter(_.dateOfOpen == openedON).toSeq.right

  override def all = repo.values.toSeq.right

  override def getCurrencyBalance(no: String, asOf: Date) = ccyBalanceRepo.get((no, asOf)).getOrElse(Seq.empty[Balance]).right

  override def getEquityBalance(no: String, asOf: Date) = equityBalanceRepo.get((no, asOf)).getOrElse(Seq.empty[Balance]).right

  override def getFixedIncomeBalance(no: String, asOf: Date) = fixedIncomeBalanceRepo.get((no, asOf)).getOrElse(Seq.empty[Balance]).right
}

object AccountRepositoryInMemory extends AccountRepositoryInMemory
