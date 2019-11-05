package sample.persistence;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.RetentionCriteria;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This is an event sourced actor. It has a state, {@link ShoppingCart.State}, which
 * stores the current shopping cart items and whether it's checked out.
 *
 * Event sourced actors are interacted with by sending them commands,
 * see classes implementing {@link ShoppingCart.Command}.
 *
 * Commands get translated to events, see classes implementing {@link ShoppingCart.Event}.
 * It's the events that get persisted by the entity. Each event will have an event handler
 * registered for it, and an event handler updates the current state based on the event.
 * This will be done when the event is first created, and it will also be done when the entity is
 * loaded from the database - each event will be replayed to recreate the state
 * of the entity.
 */
public class ShoppingCart
  extends EventSourcedBehavior<ShoppingCart.Command, ShoppingCart.Event, ShoppingCart.State> {

  /**
   * The state for the {@link ShoppingCart} entity.
   */
  public final class State implements CborSerializable {
    private Map<String, Integer> items = new HashMap<>();
    private Optional<Instant> checkoutDate = Optional.empty();

    public boolean isCheckedOut() {
      return checkoutDate.isPresent();
    }

    public Optional<Instant> getCheckoutDate() {
      return checkoutDate;
    }

    public boolean isEmpty() {
      return items.isEmpty();
    }

    public boolean hasItem(String itemId) {
      return items.containsKey(itemId);
    }

    public State updateItem(String itemId, int quantity) {
      if (quantity == 0) {
        items.remove(itemId);
      } else {
        items.put(itemId, quantity);
      }
      return this;
    }

    public State removeItem(String itemId) {
      items.remove(itemId);
      return this;
    }

    public State checkout(Instant now) {
      checkoutDate = Optional.of(now);
      return this;
    }

    public Summary toSummary() {
      return new Summary(items, isCheckedOut());
    }

  }

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  public interface Command extends CborSerializable {
  }

  /**
   * A command to add an item to the cart.
   *
   * It can reply with `Confirmation`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  public static class AddItem implements Command {
    public final String itemId;
    public final int quantity;
    public final ActorRef<Confirmation> replyTo;

    public AddItem(String itemId, int quantity, ActorRef<Confirmation> replyTo) {
      this.itemId = itemId;
      this.quantity = quantity;
      this.replyTo = replyTo;
    }
  }

  /**
   * A command to remove an item from the cart.
   */
  public static class RemoveItem implements Command {
    public final String itemId;
    public final ActorRef<Confirmation> replyTo;

    @JsonCreator
    public RemoveItem(String itemId, ActorRef<Confirmation> replyTo) {
      this.itemId = itemId;
      this.replyTo = replyTo;
    }
  }

  /**
   * A command to adjust the quantity of an item in the cart.
   */
  public static class AdjustItemQuantity implements Command {
    public final String itemId;
    public final int quantity;
    public final ActorRef<Confirmation> replyTo;

    public AdjustItemQuantity(String itemId, int quantity, ActorRef<Confirmation> replyTo) {
      this.itemId = itemId;
      this.quantity = quantity;
      this.replyTo = replyTo;
    }
  }

  /**
   * A command to get the current state of the shopping cart.
   *
   * The reply type is the {@link Summary}
   */
  public static class Get implements Command {
    public final ActorRef<Summary> replyTo;

    @JsonCreator
    public Get(ActorRef<Summary> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * A command to checkout the shopping cart.
   *
   * The reply type is the {@link Confirmation}, which will be returned when the events have been
   * emitted.
   */
  public static class Checkout implements Command {
    public final ActorRef<Confirmation> replyTo;

    @JsonCreator
    public Checkout(ActorRef<Confirmation> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  public static final class Summary implements CborSerializable {
    public final Map<String, Integer> items;
    public final boolean checkedOut;


    public Summary(Map<String, Integer> items, boolean checkedOut) {
      // Summary is included in messages and should therefore be immutable
      this.items = Collections.unmodifiableMap(new HashMap<>(items));
      this.checkedOut = checkedOut;
    }
  }

  public interface Confirmation extends CborSerializable {}

  public static class Accepted implements Confirmation {
    public final Summary summary;

    @JsonCreator
    public Accepted(Summary summary) {
      this.summary = summary;
    }
  }

  public static class Rejected implements Confirmation {
    public final String reason;

    public Rejected(String reason) {
      this.reason = reason;
    }
  }

  public interface Event extends CborSerializable {
  }

  public static final class ItemAdded implements Event {
    public final String cartId;
    public final String itemId;
    public final int quantity;

    public ItemAdded(String cartId, String itemId, int quantity) {
      this.cartId = cartId;
      this.itemId = itemId;
      this.quantity = quantity;
    }

    @Override
    public String toString() {
      return "ItemAdded(" + cartId + "," + itemId + "," + quantity + ")";
    }
  }

  public static final class ItemRemoved implements Event {
    public final String cartId;
    public final String itemId;

    public ItemRemoved(String cartId, String itemId) {
      this.cartId = cartId;
      this.itemId = itemId;
    }

    @Override
    public String toString() {
      return "ItemRemoved(" + cartId + "," + itemId + ")";
    }
  }

  public static final class ItemQuantityAdjusted implements Event {
    public final String cartId;
    public final String itemId;
    public final int quantity;

    public ItemQuantityAdjusted(String cartId, String itemId, int quantity) {
      this.cartId = cartId;
      this.itemId = itemId;
      this.quantity = quantity;
    }

    @Override
    public String toString() {
      return "ItemQuantityAdjusted(" + cartId + "," + itemId + "," + quantity + ")";
    }
  }

  public static class CheckedOut implements Event {

    public final String cartId;
    public final Instant eventTime;

    public CheckedOut(String cartId, Instant eventTime) {
      this.cartId = cartId;
      this.eventTime = eventTime;
    }

    @Override
    public String toString() {
      return "CheckedOut(" + cartId + "," + eventTime + ")";
    }
  }

  public static Behavior<Command> create(String cartId) {
    return new ShoppingCart(cartId);
  }

  private final String cartId;

  private ShoppingCart(String cartId) {
    super(PersistenceId.of("ShoppingCart", cartId),
      SupervisorStrategy.restartWithBackoff(Duration.ofMillis(200), Duration.ofSeconds(5), 0.1));
    this.cartId = cartId;
  }

  @Override
  public State emptyState() {
    return new State();
  }

  private final CheckedOutCommandHandlers checkedOutCommandHandlers = new CheckedOutCommandHandlers();
  private final OpenShoppingCartCommandHandlers openShoppingCartCommandHandlers = new OpenShoppingCartCommandHandlers();

  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    CommandHandlerBuilder<Command, Event, State> b =
      newCommandHandlerBuilder();

    b.forState(state -> !state.isCheckedOut())
      .onCommand(AddItem.class, openShoppingCartCommandHandlers::onAddItem)
      .onCommand(RemoveItem.class, openShoppingCartCommandHandlers::onRemoveItem)
      .onCommand(AdjustItemQuantity.class, openShoppingCartCommandHandlers::onAdjustItemQuantity)
      .onCommand(Checkout.class, openShoppingCartCommandHandlers::onCheckout);

    b.forState(state -> state.isCheckedOut())
      .onCommand(AddItem.class, checkedOutCommandHandlers::onAddItem)
      .onCommand(RemoveItem.class, checkedOutCommandHandlers::onRemoveItem)
      .onCommand(AdjustItemQuantity.class, checkedOutCommandHandlers::onAdjustItemQuantity)
      .onCommand(Checkout.class, checkedOutCommandHandlers::onCheckout);

    b.forAnyState()
      .onCommand(Get.class, this::onGet);

    return b.build();
  }

  private Effect<Event, State> onGet(State state, Get cmd) {
    cmd.replyTo.tell(state.toSummary());
    return Effect().none();
  }

  private class OpenShoppingCartCommandHandlers {

    Effect<Event, State> onAddItem(State state, AddItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        cmd.replyTo.tell(new Rejected(
          "Item '" + cmd.itemId + "' was already added to this shopping cart"));
        return Effect().none();
      } else if (cmd.quantity <= 0) {
        cmd.replyTo.tell(new Rejected("Quantity must be greater than zero"));
        return Effect().none();
      } else {
        return Effect().persist(new ItemAdded(cartId, cmd.itemId, cmd.quantity))
          .thenRun(updatedCart -> cmd.replyTo.tell(new Accepted(updatedCart.toSummary())));
      }
    }

    Effect<Event, State> onRemoveItem(State state, RemoveItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        return Effect().persist(new ItemRemoved(cartId, cmd.itemId))
          .thenRun(updatedCart -> cmd.replyTo.tell(new Accepted(updatedCart.toSummary())));
      } else {
        cmd.replyTo.tell(new Accepted(state.toSummary()));
        return Effect().none();
      }
    }

    Effect<Event, State> onAdjustItemQuantity(State state, AdjustItemQuantity cmd) {
      if (cmd.quantity <= 0) {
        cmd.replyTo.tell(new Rejected("Quantity must be greater than zero"));
        return Effect().none();
      } else if (state.hasItem(cmd.itemId)) {
        return Effect().persist(new ItemQuantityAdjusted(cartId, cmd.itemId, cmd.quantity))
          .thenRun(updatedCart -> cmd.replyTo.tell(new Accepted(updatedCart.toSummary())));
      } else {
        cmd.replyTo.tell(new Rejected(
          "Cannot adjust quantity for item '" + cmd.itemId + "'. Item not present on cart"));
        return Effect().none();
      }
    }

    Effect<Event, State> onCheckout(State state, Checkout cmd) {
      if (state.isEmpty()) {
        cmd.replyTo.tell(new Rejected("Cannot checkout an empty shopping cart"));
        return Effect().none();
      } else {
        return Effect().persist(new CheckedOut(cartId, Instant.now()))
          .thenRun(updatedCart -> cmd.replyTo.tell(new Accepted(updatedCart.toSummary())));
      }
    }
  }

  private class CheckedOutCommandHandlers {
    Effect<Event, State> onAddItem(AddItem cmd) {
      cmd.replyTo.tell(new Rejected("Can't add an item to an already checked out shopping cart"));
      return Effect().none();
    }

    Effect<Event, State> onRemoveItem(RemoveItem cmd) {
      cmd.replyTo.tell(new Rejected("Can't remove an item from an already checked out shopping cart"));
      return Effect().none();
    }

    Effect<Event, State> onAdjustItemQuantity(AdjustItemQuantity cmd) {
      cmd.replyTo.tell(new Rejected("Can't adjust item on an already checked out shopping cart"));
      return Effect().none();
    }

    Effect<Event, State> onCheckout(Checkout cmd) {
      cmd.replyTo.tell(new Rejected("Can't checkout already checked out shopping cart"));
      return Effect().none();
    }
  }

  @Override
  public EventHandler<State, Event> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
      .onEvent(ItemAdded.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
      .onEvent(ItemRemoved.class, (state, event) -> state.removeItem(event.itemId))
      .onEvent(ItemQuantityAdjusted.class, (state, event) -> state.updateItem(event.itemId, event.quantity))
      .onEvent(CheckedOut.class, (state, event) -> state.checkout(event.eventTime))
      .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    // enable snapshotting
    return RetentionCriteria.snapshotEvery(100, 3);
  }
}
