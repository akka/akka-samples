package sample.cqrs

import java.time.Instant

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
  final case class State(items: Map[String, Int], checkedOut: Boolean) {

    def hasItem(itemId: String): Boolean =
      items.contains(itemId)

    def isEmpty: Boolean =
      items.isEmpty

    def updateItem(itemId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }
    }

    def removeItem(itemId: String): State =
      copy(items = items - itemId)

    def checkout(): State = copy(checkedOut = true)

    def toSummary: Summary =
      Summary(items, checkedOut)
  }
  object State {
    val empty = State(Map.empty, checkedOut = false)
  }

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  sealed trait Command extends CborSerializable

  /**
   * A command to add an item to the cart.
   *
   * It can reply with `Confirmation`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends Command

  /**
   * A command to remove an item from the cart.
   */
  final case class RemoveItem(itemId: String, replyTo: ActorRef[Confirmation]) extends Command

  /**
   * A command to adjust the quantity of an item in the cart.
   */
  final case class AdjustItemQuantity(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends Command

  /**
   * A command to checkout the shopping cart.
   */
  final case class Checkout(replyTo: ActorRef[Confirmation]) extends Command

  /**
   * A command to get the current state of the shopping cart.
   */
  final case class Get(replyTo: ActorRef[Summary]) extends Command

  final case class Summary(items: Map[String, Int], checkedOut: Boolean) extends CborSerializable

  sealed trait Confirmation extends CborSerializable

  final case class Accepted(summary: Summary) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event

  final case class ItemRemoved(cartId: String, itemId: String) extends Event

  final case class ItemQuantityAdjusted(cartId: String, itemId: String, newQuantity: Int) extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

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
          //The shopping cart behavior changes if it's checked out or not.
          // The commands are handled differently for each case.
          if (state.checkedOut) checkedOutShoppingCart(cartId, state, command)
          else openShoppingCart(cartId, state, command),
        (state, event) => handleEvent(state, event))
      .withTagger(_ => eventProcessorTags)
  }

  private def openShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case AddItem(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(Rejected(s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
        else
          Effect
            .persist(ItemAdded(cartId, itemId, quantity))
            .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))

      case RemoveItem(itemId, replyTo) =>
        if (state.hasItem(itemId))
          Effect.persist(ItemRemoved(cartId, itemId)).thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(Accepted(state.toSummary)) // removing an item is idempotent

      case AdjustItemQuantity(itemId, quantity, replyTo) =>
        if (quantity <= 0)
          Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
        else if (state.hasItem(itemId))
          Effect
            .persist(ItemQuantityAdjusted(cartId, itemId, quantity))
            .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))
        else
          Effect.reply(replyTo)(Rejected(s"Cannot adjust quantity for item '$itemId'. Item not present on cart"))

      case Checkout(replyTo) =>
        if (state.isEmpty)
          Effect.reply(replyTo)(Rejected("Cannot checkout an empty shopping cart"))
        else
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenReply(replyTo)(updatedCart => Accepted(updatedCart.toSummary))

      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }

  private def checkedOutShoppingCart(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
      case cmd: AddItem =>
        Effect.reply(cmd.replyTo)(Rejected("Can't add an item to an already checked out shopping cart"))
      case cmd: RemoveItem =>
        Effect.reply(cmd.replyTo)(Rejected("Can't remove an item from an already checked out shopping cart"))
      case cmd: AdjustItemQuantity =>
        Effect.reply(cmd.replyTo)(Rejected("Can't adjust item on an already checked out shopping cart"))
      case cmd: Checkout =>
        Effect.reply(cmd.replyTo)(Rejected("Can't checkout already checked out shopping cart"))
    }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case ItemAdded(_, itemId, quantity)            => state.updateItem(itemId, quantity)
      case ItemRemoved(_, itemId)                    => state.removeItem(itemId)
      case ItemQuantityAdjusted(_, itemId, quantity) => state.updateItem(itemId, quantity)
      case CheckedOut(_, _)                          => state.checkout()
    }
  }
}
