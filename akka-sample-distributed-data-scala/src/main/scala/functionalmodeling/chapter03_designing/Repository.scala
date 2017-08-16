package functionalmodeling.chapter03_designing

import java.util.Date

import functionalmodeling.chapter03_designing.Smarter._

import scala.util.Try

/**
  *
  */
trait Repository[A, IdType] {
  def query(id: IdType): Try[Option[A]]
  def store(a: A): Try[A]
}

trait AccountRepository extends Repository[smartconstructor.Account, String] {
  def query(accountNo: String): Try[Option[smartconstructor.Account]]

  def store(a: smartconstructor.Account): Try[smartconstructor.Account]

  def balance(accountNo: String): Try[smartconstructor.Balance]

  def openedOn(date: Date): Try[Seq[smartconstructor.Account]]
}

trait CustomerRepository extends Repository[lens.Customer, String] {
  def address(customerNo: String): Try[Option[lens.Address]]
}

//trait CustomerAndAccountRepository extends AccountRepository with CustomerRepository

trait AccountService[Account, Amount, Balance] {
  def open(no: String, name: String, openingDate: Option[Date]): Reader[AccountRepository, Try[Account]]
  def close(no: String, closeDate: Option[Date]): Reader[AccountRepository, Try[Account]]
  def debit(no: String, amount: Amount): Reader[AccountRepository, Try[Account]]
  def credit(no: String, amount: Amount): Reader[AccountRepository, Try[Account]]
  def balance(no: String): Reader[AccountRepository, Try[Balance]]


}

/*object AccountService extends AccountService[Account, Amount, Balance] {
  override def open(no: String, name: String, openingDate: Option[Date]) = repo =>  for {
    a <- Account.checkingAccount(no, name, openingDate, None, Balance())
    o <- repo.store(a)
  } yield o


  override def close(no: String, closeDate: Option[Date]) = repo => for {
    a <- Account.checkingAccount(no, "", closeDate, closeDate, Balance())
    b <- repo.store(a)
  } yield b

  override def debit(no: String, amount: Amount) = repo => repo.query(no).flatMap { a =>

    if (a.isDefined) {
      val aa = a.get
      Account.checkingAccount(aa.no, aa.name, aa.dateOfOpen, aa.dateOfClose, Balance(aa.balance.amount - amount))
    } else throw new Exception("Insufficient account")

  }

  override def credit(no: String, amount: Amount) = repo => repo.query(no).flatMap { a =>
    if (a.isDefined) {
      val aa = a.get
      Account.checkingAccount(aa.no, aa.name, aa.dateOfOpen, aa.dateOfClose, Balance(aa.balance.amount + amount))
    } else throw new Exception("Insufficient account")
  }

  override def balance(no: String) = repo => repo.query(no).map { a =>
    a.map(_.balance).getOrElse(Balance())
  }
}*/

case class Reader[R, A](run: R => A) {
  def map[B](f: A => B): Reader[R, B] = Reader( r => f(run(r)))

  def flatMap[B](f: A => Reader[R, B]): Reader[R, B] = Reader(r => f(run(r)).run(r))
}

trait App extends AccountService[smartconstructor.Account, Amount, smartconstructor.Balance] {
  def op(no: String) = for {
    _ <- credit(no, BigDecimal(100))
    _ <- credit(no, BigDecimal(200))
    _ <- debit(no, BigDecimal(160))
    b <- balance(no)
  } yield b
}