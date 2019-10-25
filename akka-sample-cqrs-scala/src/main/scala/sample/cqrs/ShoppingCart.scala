package sample.cqrs

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect

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
    val empty = State(Map.empty, checkedOut = false)
  }

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  sealed trait Command extends CborSerializable

  sealed trait Result extends CborSerializable
  case object OK extends Result
  case class Rejected(msg: String) extends Result

  /**
   * A command to update an item.
   *
   * It can reply with `Result`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  case class UpdateItem(productId: String, quantity: Int, replyTo: ActorRef[Result]) extends Command

  /**
   * A command to get the current state of the shopping cart.
   *
   * It can reply with the state of the ShoppingCart
   */
  case class Get(replyTo: ActorRef[State]) extends Command

  /**
   * A command to checkout the shopping cart.
   *
   * It can reply with `Result`, which will be returned when the fact that checkout
   * has started has successfully been persisted.
   */
  case class Checkout(replyTo: ActorRef[Result]) extends Command

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  /**
   * An event that represents a item updated event.
   */
  case class ItemUpdated(cartId: String, productId: String, quantity: Int) extends Event

  /**
   * An event that represents a checked out event.
   */
  case class CheckedOut(cartId: String) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  def init(system: ActorSystem[_]): Unit = {
    val eventProcessorSettings = EventProcessorSettings(system)
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      val n = math.abs(entityContext.entityId.hashCode % eventProcessorSettings.parallelism)
      val eventProcessorTag = eventProcessorSettings.tagPrefix + "-" + n
      ShoppingCart(entityContext.entityId, Set(eventProcessorTag))
    }.withRole("write-model"))
  }

  def apply(cartId: String, eventProcessorTags: Set[String]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        PersistenceId(EntityKey.name, cartId),
        State.empty,
        (state, command) =>
          if (state.checkedOut) checkedOutShoppingCart(cartId, state, command)
          else openShoppingCart(cartId, state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventProcessorTags)
  }

  private def openShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case cmd @ UpdateItem(productId, quantity, _) =>
        if (quantity < 0)
          Effect.reply(cmd.replyTo)(Rejected("Quantity must be greater than zero"))
        else if (quantity == 0 && !state.items.contains(productId))
          Effect.reply(cmd.replyTo)(Rejected("Cannot delete item that is not already in cart"))
        else
          Effect.persist(ItemUpdated(cartId, productId, quantity)).thenReply(cmd.replyTo)(_ => OK)

      case cmd: Checkout =>
        if (state.items.isEmpty)
          Effect.reply(cmd.replyTo)(Rejected("Cannot checkout empty cart"))
        else
          Effect.persist(CheckedOut(cartId)).thenReply(cmd.replyTo)(_ => OK)

      case cmd: Get =>
        Effect.reply(cmd.replyTo)(state)
    }

  private def checkedOutShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case cmd: Get =>
        Effect.reply(cmd.replyTo)(state)
      case cmd: UpdateItem =>
        Effect.reply(cmd.replyTo)(Rejected("Can't update item on already checked out shopping cart"))
      case cmd: Checkout =>
        Effect.reply(cmd.replyTo)(Rejected("Can't checkout already checked out shopping cart"))
    }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case ItemUpdated(_, productId, quantity) => state.updateItem(productId, quantity)
      case CheckedOut(_)                       => state.checkout
    }
  }
}
