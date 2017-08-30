package functionalmodeling.chapter05_modularization
package free

import scala.collection.mutable.{Map => MMap}

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import Task.{now, fail}

/**
  *
  */
trait AccountRepoInterpreter {
  def apply[A](action: AccountRepo[A]): Task[A]
}

case class AccountRepoMutableInterpreter() extends AccountRepoInterpreter {

  val table: MMap[String, Account] = MMap.empty[String, Account]

  val step: AccountRepoF ~> Task = new (AccountRepoF ~> Task) {
    override def apply[A](fa: AccountRepoF[A]): Task[A] = fa match {
      case Query(no) =>
        table.get(no).map { a => now(a) }
          .getOrElse { fail(new RuntimeException(s"Account no $no not found")) }

      case Store(account) => now(table += ((account.no, account))).void
      case Delete(no) => now(table -= no).void
    }
  }

  override def apply[A](action: AccountRepo[A]): Task[A] = action.foldMap(step)
}

/*case class AccountRepoShowInterpreter() {
  type ListState[A] = State[List[String], A]

  val step: AccountRepoF ~> ListState = new (AccountRepoF ~> ListState) {
    private def show(s: String): ListState[Unit] = State(l => (l ++ List(s), ()))

    override def apply[A](fa: AccountRepoF[A]): ListState[A] = fa match {
      case Query(no) => show(s"Query for $no").map(_ => Account(no, ""))
      case Store(account) => show(s"Storing $account")
      case Delete(no) => show(s"deleting $no")
    }
  }

  def interpret[A](script: AccountRepo[A], ls: List[String]): List[String] =
    script.foldMap(step).exec(ls)
}*/
