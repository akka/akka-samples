package functionalmodeling.chapter05_modularization
package domain
package repository
package interpreter

import java.util.Date

import scala.collection.mutable.{Map => MMap}

import scalaz._
import Scalaz._
import \/._

import model.{ Account, Balance }

trait AccountRepositoryInMemory extends AccountRepository {

  lazy val repo = MMap.empty[String, Account]

  override def query(no: String): \/[NonEmptyList[String], Option[Account]] = repo.get(no).right

  override def store(a: Account): \/[NonEmptyList[String], Account] = {
    val r = repo += (a.no -> a)
    a.right
  }

  override def query(openedOn: Date): \/[NonEmptyList[String], Seq[Account]] =
    repo.values.filter(_.dateOfOpen == openedOn).toSeq.right

  override def all: \/[NonEmptyList[String], Seq[Account]] = repo.values.toSeq.right
}

object AccountRepositoryInMemory extends AccountRepositoryInMemory
