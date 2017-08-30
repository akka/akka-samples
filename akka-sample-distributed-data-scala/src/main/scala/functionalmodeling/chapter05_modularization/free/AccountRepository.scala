package functionalmodeling.chapter05_modularization
package free

import common._

import scalaz.Free

/**
  * Free Repository
  */

/**
  * Defining the building blocks
  */
sealed trait AccountRepoF[+A]
case class Query(no: String) extends AccountRepoF[Account]
case class Store(account: Account) extends AccountRepoF[Unit]
case class Delete(no: String) extends AccountRepoF[Unit]

/**
  * The magic band - get a free monad
  */
// type AccountRepo[A] = Free[AccountRepoF, A]

/**
  * Defining the module
  */
trait AccountRepository {
  def store(account: Account): AccountRepo[Unit] =
    Free.liftF(Store(account))

  def query(no: String): AccountRepo[Account] =
    Free.liftF(Query(no))

  def delete(no: String): AccountRepo[Unit] =
    Free.liftF(Delete(no))

  def update(no: String, f: Account => Account): AccountRepo[Unit] = for {
    a <- query(no)
    _ <- store(f(a))
  } yield ()

  def updateBalance(no: String, amount: Amount, f: (Account, Amount) => Account): AccountRepo[Unit] = for {
    a <- query(no)
    _ <- store(f(a, amount))
  } yield ()
  
}

object AccountRepository extends AccountRepository