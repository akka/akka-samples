package functionalmodeling.chapter08_persistence
package cqrs
package memrepo

import scala.language.higherKinds

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import Task._
import collection.concurrent.TrieMap

import common._

import org.joda.time.DateTime
import spray.json._

trait Event[A] {
  def at: DateTime
}

case class Opened(no: String, name: String, openingDate: Option[DateTime], at: DateTime = today) extends Event[Account]
case class Closed(no: String, closeDate: Option[DateTime], at: DateTime = today) extends Event[Account]
case class Debited(no: String, amount: Amount, at: DateTime = today) extends Event[Account]
case class Credited(no: String, amount: Amount, at: DateTime = today) extends Event[Account]

object Event {
  val eventLog = TrieMap[String, List[Event[_]]]()
  val eventLogJson = TrieMap[String, List[String]]()

  def updateState(e: Event[_], initial: Map[String, Account]) = e match {
    case o @ Opened(no, name, odate, _) =>
      initial + (no -> Account(no, name, odate.get))

    case c @ Closed(no, cdate, at) =>
      initial + (no -> initial(no).copy(dateOfClosing = Some(cdate.getOrElse(at))))

    case d @ Debited(no, amount, _) =>
      val a = initial(no)
      initial + (no -> a.copy(balance = Balance(a.balance.amount - amount)))

    case r @ Credited(no, amount, _) =>
      val a = initial(no)
      initial + (no -> a.copy(balance = Balance(a.balance.amount + amount)))

  }

  def events(no: String): Error \/ List[Event[_]] = {
    val currentList = eventLog.getOrElse(no, Nil)
    if (currentList.isEmpty) s"$Account $no does not exist".left
    else currentList.right
  }

  def snapshot(es: List[Event[_]]): String \/ Map[String, Account] =
    es.reverse.foldLeft(Map.empty[String, Account]) { (a, e) => updateState(e, a) }.right

  def snapshotFromJson(es: List[String]): String \/ Map[String, Account] =
    es.reverse.foldLeft(Map.empty[String, Account]) { (a, e) => updateState(e.parseJson.convertTo[Event[_]], a) }.right

}

object Commands extends Commands {
  import Event._

}

trait Commands {
  import Event._
  import scala.language.implicitConversions

  type Command[A] = Free[Event, A]

  
}
class AccountService {

}
