package functionalmodeling.chapter06_reactive
package task
package repository

import java.util.Date

import scalaz._
import Scalaz._
import \/._
import model._

trait AccountRepository {
  def query(no: String): \/[NonEmptyList[String], Option[Account]]
  def store(a: Account): \/[NonEmptyList[String], Account]
  def query(openedON: Date): \/[NonEmptyList[String], Seq[Account]]
  def all: \/[NonEmptyList[String], Seq[Account]]

  def getCurrencyBalance(no: String, asOf: Date): String \/ Seq[Balance]
  def getEquityBalance(no: String, asOf: Date): String \/ Seq[Balance]
  def getFixedIncomeBalance(no: String, asOf: Date): String \/ Seq[Balance]
}
