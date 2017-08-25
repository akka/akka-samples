package functionalmodeling.chapter05_modularization
package domain
package repository

import java.util.Date

import scalaz.NonEmptyList
import scalaz._
import Scalaz._
import \/._

import model.{Account, Balance}

trait AccountRepository {

  def query(no: String): \/[NonEmptyList[String], Option[Account]]

  def store(a: Account): \/[NonEmptyList[String], Account]

  def balance(no: String): \/[NonEmptyList[String], Balance] = query(no) match {
    case \/-(Some(a)) => a.balance.right
    case \/-(None) => NonEmptyList(s"No account exists with no $no").left[Balance]
    case a @ -\/(_) => a
  }

  def query(openedOn: Date): \/[NonEmptyList[String], Seq[Account]]

  def all: \/[NonEmptyList[String], Seq[Account]]
}
