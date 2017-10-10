package akka_tut.stream

import akka.actor.{Actor, Cancellable, Scheduler}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * http://blog.colinbreck.com/akka-streams-a-motivating-example/
  */

class Database() {
  def bulkInsertAsync(messages: Seq[String]): Unit = ()
}

object DatabaseActor {

  case class InsertMessage(message: String)

  case object Insert

  case object Decrement

}
class DatabaseActor extends Actor {

  import DatabaseActor._

  val database = new Database()

  var messages: Seq[String] = Nil
  var count: Int = 0
  var flush = true
  var outstanding = 0

  var scheduler: Cancellable = _

  override def preStart(): Unit = {
    scheduler = context.system.scheduler.scheduleOnce(1 second) {
      self ! Insert
    }
  }

  override def postStop(): Unit = {
    scheduler.cancel()
  }
  override def receive: Receive = {
    case InsertMessage(message) =>
      messages = message +: messages
      count += 1
      
      if (count == 1000) {
        insert()
        flush = false
      }


    case Insert =>
      if (flush) insert() else flush = true
      context.system.scheduler.scheduleOnce(1 second) {
        self ! Insert
      }

    case Decrement =>
      outstanding -= 1
      if (count >= 1000) {
        insert()
        flush = false
      }
  }

  private def insert() = {
    if (count > 0 && outstanding < 10) {
      outstanding += 1
      val (insert, remaining) = messages.splitAt(1000)
      messages = remaining
      count = remaining.size
      database.bulkInsertAsync(insert) /*andThen {
        case _ => self ! Decrement
      }*/
      self ! Decrement
      //messages = Nil
      //count = 0
    }

  }
}
