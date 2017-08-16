package functionalmodeling.chapter03_designing.repository

import java.util.Date

import scala.util.{Failure, Success, Try}
import scala.collection.mutable.{Map => MMap}

/**
  *
  */
trait AccountRepository extends Repository[Account, String] {
  def query(no: String): Try[Option[Account]]
  def store(a: Account): Try[Account]
  def balance(no: String): Try[Balance] = query(no) match {
    case Success(Some(a)) => Success(a.balance)
    case Success(None) => Failure(new Exception(s"No account exists with no $no"))
    case Failure(ex) => Failure(ex)
  }

  def query(openedDate: Date): Try[Seq[Account]]
}

trait AccountRepositoryInMemory extends AccountRepository {
  lazy val repo = MMap.empty[String, Account]

  def query(no: String): Try[Option[Account]] = Success(repo.get(no))
  def store(a: Account): Try[Account] = {
    repo += ((a.no -> a))
    Success(a)
  }

  def query(openedOn: Date): Try[Seq[Account]] = Success(repo.values.filter(_.dateOfOpening == openedOn).toSeq)
}

object AccountRepositoryInMemory extends AccountRepositoryInMemory
