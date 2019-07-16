package sample.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.ExpectingReply
import akka.persistence.typed.scaladsl.ReplyEffect

import akka.actor.testkit.typed.scaladsl.Effects

object ShoppingCart {
  /**
   * The current state held by the persistent entity.
   */
  case class State(items: Map[String, Int], checkedOut: Boolean) {
    def updateItem(productId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - productId)
        case _ => copy(items = items + (productId -> quantity))
      }
    }

    def checkout: State = copy(checkedOut = true)
  }
  object State {
    val empty = State(Map.empty, false)
  }

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  sealed trait Command[R] extends ExpectingReply[R]

  sealed trait Result
  case object OK extends Result
  case class Failed(msg: String) extends Result

  /**
   * A command to update an item.
   *
   * It can reply with `Done`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  case class UpdateItem(productId: String, quantity: Int, replyTo: ActorRef[Result])
      extends Command[Result]

  /**
   * A command to get the current state of the shopping cart.
   *
   * It can reply with the state of the ShoppingCart
   */
  case class Get(replyTo: ActorRef[State]) extends Command[State]

  /**
   * A command to checkout the shopping cart.
   *
   * It can reply with Done, which will be returned when the fact that checkout
   * has started has successfully been persisted.
   */
  case class Checkout(replyTo: ActorRef[Result]) extends Command[Result]

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event

  /**
   * An event that represents a item updated event.
   */
  case class ItemUpdated(productId: String, quantity: Int) extends Event

  /**
   * An event that represents a checked out event.
   */
  case object CheckedOut extends Event

  def behavior(id: String): Behavior[Command[_]] =
    EventSourcedBehavior.withEnforcedReplies[Command[_], Event, State](
      PersistenceId(s"ShoppingCart|$id"),
      State.empty,
      (state, command) =>
        if (state.checkedOut) checkedOutShoppingCart(state, command)
        else openShoppingCart(state, command),
      (state, event) =>
        event match {
          case ItemUpdated(productId, quantity) => state.updateItem(productId, quantity)
          case CheckedOut                       => state.checkout
        })

  def openShoppingCart(state: State, command: Command[_]): ReplyEffect[Event, State] = command match {
    case cmd @ UpdateItem(_, quantity, _) if quantity < 0 =>
      Effect.reply(cmd)(Failed("Quantity must be greater than zero"))
    case cmd @ UpdateItem(productId, 0, _) if !state.items.contains(productId) =>
      Effect.reply(cmd)(Failed("Cannot delete item that is not already in cart"))
    case cmd @ UpdateItem(productId, quantity, _) =>
      Effect.persist(ItemUpdated(productId, quantity)).thenReply(cmd)(_ => OK)

    case cmd: Checkout if state.items.isEmpty =>
      Effect.reply(cmd)(Failed("Cannot checkout empty cart"))
    case cmd: Checkout =>
      Effect.persist(CheckedOut).thenReply(cmd)(_ => OK)

    case cmd: Get =>
      Effect.reply(cmd)(state)
  }

  def checkedOutShoppingCart(state: State, command: Command[_]): ReplyEffect[Event, State] = command match {
    case cmd: Get =>
      Effect.reply(cmd)(state)
    case cmd: UpdateItem =>
      Effect.reply(cmd)(Failed("Can't update item on already checked out shopping cart"))
    case cmd: Checkout =>
      Effect.reply(cmd)(Failed("Can't checkout already checked out shopping cart"))
  }
}
