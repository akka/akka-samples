package functionalmodeling.chapter03_designing
package repository
package reader

import common._

import java.util._

import scala.util._

trait AccountService[Account, Amount, Balance] {
  def open(no: String, name: String, openingDate: Option[Date]): Reader[AccountRepository, Try[Account]]
  def close(no: String, closeDate: Option[Date]): Reader[AccountRepository, Try[Account]]
  def debit(no: String, amount: Amount): Reader[AccountRepository, Try[Account]]
  def credit(no: String, amount: Amount): Reader[AccountRepository, Try[Account]]
  def balance(no: String): Reader[AccountRepository, Try[Balance]]
}

object AccountService extends AccountService[Account, Amount, Balance] {

  def open(no: String, name: String, openingDate: Option[Date]) = Reader { (repo: AccountRepository) =>
    repo.query(no) match {
      case Success(Some(a)) => Failure(new Exception(s"Already existing account with no $no"))
      case Success(None) =>
        if (no.isEmpty || name.isEmpty) Failure(new Exception(s"Account no or name cannot be blank") )
        else if (openingDate.getOrElse(today) before today) Failure(new Exception(s"Cannot open account in the past"))
        else repo.store(Account(no, name, openingDate.getOrElse(today)))
      case Failure(ex) => Failure(new Exception(s"Failed to open account $no: $name", ex))
    }
  }

  def close(no: String, closeDate: Option[Date]) = Reader { (repo: AccountRepository) =>
    repo.query(no) match {
      case Success(Some(a)) =>
        if (closeDate.getOrElse(today) before a.dateOfOpening)
          Failure(new Exception(s"Close date $closeDate cannot be before opening date ${a.dateOfOpening}"))
        else repo.store(a.copy(dateOfClosing = closeDate))
      case Success(None) => Failure(new Exception(s"Account not found with $no"))
      case Failure(ex) => Failure(new Exception(s"Fail in closing account $no", ex))
    }
  }

  def debit(no: String, amount: Amount) = Reader { (repo: AccountRepository) =>
    repo.query(no) match {
      case Success(Some(a)) =>
        if (a.balance.amount < amount) Failure(new Exception("Insufficient balance"))
        else repo.store(a.copy(balance = Balance(a.balance.amount - amount)))
      case Success(None) => Failure(new Exception(s"Account not found with $no"))
      case Failure(ex) => Failure(new Exception(s"Fail in debit from $no amount $amount", ex))
    }
  }

  def credit(no: String, amount: Amount) = Reader { (repo: AccountRepository) =>
    repo.query(no) match {
      case Success(Some(a)) => repo.store(a.copy(balance = Balance(a.balance.amount + amount)))
      case Success(None) => Failure(new Exception(s"Account not found with $no"))
      case Failure(ex) => Failure(new Exception(s"Fail in credit to $no amount $amount", ex))
    }
  }

  def balance(no: String) = Reader((repo: AccountRepository) => repo.balance(no))
}

object ReaderApp {
  import AccountService._

  def op(no: String) = for {
    _ <- credit(no, BigDecimal(100))
    _ <- credit(no, BigDecimal(200))
    _ <- debit(no, BigDecimal(160))
    b <- balance(no)
  } yield b

  def main(args: Array[String]): Unit = {
    val repo = AccountRepositoryInMemory

    val no = "james"
    println(open(no, no, Some(today)).run(repo))
    println(s"the balance after op: ${op(no).run(repo)}")
  }
}