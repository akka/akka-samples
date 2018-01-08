package sample.persistence.multidc

import akka.persistence.multidc.scaladsl.ReplicatedEntity;

object ThumbsUpCounter {
  sealed trait Command

  final case class GiveThumbsUp(resourceId: String, userId: String) extends Command

  final case class GetCount(resourceId: String) extends Command

  final case class GetUsers(resourceId: String) extends Command

  sealed trait Event

  final case class GaveThumbsUp(userId: String) extends Event

  final case class State(users: Set[String]) {
    def add(userId: String): State = copy(users + userId)
  }
}

class ThumbsUpCounter
  extends ReplicatedEntity[ThumbsUpCounter.Command, ThumbsUpCounter.Event, ThumbsUpCounter.State] {
  import ThumbsUpCounter._

  override def initialState: State = State(Set.empty)

  override def commandHandler: CommandHandler = CommandHandler { (ctx, state, cmd) =>
    cmd match {
      case GiveThumbsUp(_, userId) =>
        Effect.persist(GaveThumbsUp(userId))
      case GetCount(_) =>
        ctx.sender() ! state.users.size
        Effect.none
      case GetUsers(_) =>
        ctx.sender() ! state
        Effect.none
    }
  }

  override def eventHandler(state: State, event: Event): State = {
    event match {
      case GaveThumbsUp(userId) => state.add(userId)
    }
  }

}
