package functionalmodeling.chapter05_modularization
package domain
package service
package interpreter

import scalaz._
import Scalaz._
import Kleisli._

import repository.AccountRepository
import model.common._

class ReportingServiceInterpreter extends ReportingService[Amount] {
  override def balanceByAccount: ReportOperation[Seq[(String, Amount)]] = kleisli { (repo: AccountRepository) =>
    repo.all match {
      case \/-(as) => as.map(a => (a.no, a.balance.amount)).right
      case a @ -\/(_) => a
    }
  }
}

object ReportingService extends ReportingServiceInterpreter
