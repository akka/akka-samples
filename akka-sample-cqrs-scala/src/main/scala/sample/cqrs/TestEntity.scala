package sample.cqrs

import akka.actor.ActorLogging
import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.journal.Tagged

object TestEntity {
  def props(): Props =
    Props(new TestEntity)
}

class TestEntity extends PersistentActor with ActorLogging {
  override def persistenceId: String = "TestEntity|" + context.self.path.name

  override def receiveRecover: Receive = {
    case event: String =>
  }

  override def receiveCommand: Receive = {
    case command: String =>
      val event = Tagged(command, Set("tag1"))
      persist(event) {_ =>
        log.info("persisted {}", event)
      }
  }

}
