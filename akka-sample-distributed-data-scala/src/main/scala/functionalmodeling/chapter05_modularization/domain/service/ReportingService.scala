package functionalmodeling.chapter05_modularization
package domain
package service

import scalaz._

import repository.AccountRepository

trait ReportingService[Account] {
  type ReportOperation[A] = Kleisli[Valid, AccountRepository, A]

  def balanceByAccount: ReportOperation[Seq[(String, Account)]]
}
