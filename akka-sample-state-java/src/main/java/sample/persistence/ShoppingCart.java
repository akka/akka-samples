package sample.persistence;

//import akka.actor.typed.ActorRef;
//import akka.actor.typed.Behavior;
//import akka.actor.typed.SupervisorStrategy;
//import akka.pattern.StatusReply;
//
//import akka.persistence.typed.PersistenceId;
//import akka.persistence.typed.state.javadsl.DurableStateBehavior;
//import akka.persistence.typed.state.javadsl.Effect;
//import com.fasterxml.jackson.annotation.JsonCreator;
//
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.pattern.StatusReply;
import akka.persistence.typed.state.javadsl.*;
// #behavior
import akka.persistence.typed.PersistenceId;

// #behavior

// #effects
import akka.Done;
import com.fasterxml.jackson.annotation.JsonCreator;
// #effects

import java.time.Duration;

/**
 *
 */
public class ShoppingCart
  extends DurableStateBehavior<ShoppingCart.Command, ShoppingCart.State> {

  /**
   * The state for the {@link ShoppingCart} entity.
   */
  public final class State implements CborSerializable {
    private boolean isThat = false;
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

    public boolean getIsThat() {
      return isThat;
    }

    public void setIsThat(boolean newValue) {
      isThat = newValue;
    }


    public State(Map<String, Integer> items, Optional<Instant> checkoutDate) {
      this.items = items;
      this.checkoutDate = checkoutDate;
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
      return new Summary(items, isCheckedOut(), getIsThat());
    }

  }

  /**
   * This interface defines all the commands that the ShoppingCart persistent actor supports.
   */
  public interface Command extends CborSerializable {
  }

  public static class GetState implements Command {
    public final ActorRef<StatusReply<Summary>> replyTo;
    @JsonCreator
    public GetState(ActorRef<StatusReply<Summary>> replyTo) {
      this.replyTo = replyTo;
    }
  }


  public static class MutateState implements Command {
    public final ActorRef<StatusReply<Summary>> replyTo;
    @JsonCreator
    public MutateState(ActorRef<StatusReply<Summary>> replyTo) {
      this.replyTo = replyTo;
    }
  }


  /**
   * A command to add an item to the cart.
   *
   */
  public static class AddItem implements Command {
    public final String itemId;
    public final int quantity;
    public final ActorRef<StatusReply<Summary>> replyTo;

    public AddItem(String itemId, int quantity, ActorRef<StatusReply<Summary>> replyTo) {
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
    public final ActorRef<StatusReply<Summary>> replyTo;

    @JsonCreator
    public RemoveItem(String itemId, ActorRef<StatusReply<Summary>> replyTo) {
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
    public final ActorRef<StatusReply<Summary>> replyTo;

    public AdjustItemQuantity(String itemId, int quantity, ActorRef<StatusReply<Summary>> replyTo) {
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
   */
  public static class Checkout implements Command {
    public final ActorRef<StatusReply<Summary>> replyTo;

    @JsonCreator
    public Checkout(ActorRef<StatusReply<Summary>> replyTo) {
      this.replyTo = replyTo;
    }
  }

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  public static final class Summary implements CborSerializable {
    public final Map<String, Integer> items;
    public final boolean checkedOut;

    public final boolean value;

    public Summary(Map<String, Integer> items, boolean checkedOut, boolean value) {
      // Summary is included in messages and should therefore be immutable
      this.items = Collections.unmodifiableMap(new HashMap<>(items));
      this.checkedOut = checkedOut;
      this.value = value;
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
    return new State(new HashMap<>(), Optional.empty());
  }

  private final CheckedOutCommandHandlers checkedOutCommandHandlers = new CheckedOutCommandHandlers();
  private final OpenShoppingCartCommandHandlers openShoppingCartCommandHandlers = new OpenShoppingCartCommandHandlers();

  @Override
  public CommandHandler<Command, State> commandHandler() {
    CommandHandlerBuilder<Command, State> b =
      newCommandHandlerBuilder();

    b.forState(state -> !state.isCheckedOut())
      .onCommand(MutateState.class, openShoppingCartCommandHandlers::onMutateState)
      .onCommand(AddItem.class, openShoppingCartCommandHandlers::onAddItem)
      .onCommand(RemoveItem.class, openShoppingCartCommandHandlers::onRemoveItem)
      .onCommand(AdjustItemQuantity.class, openShoppingCartCommandHandlers::onAdjustItemQuantity)
      .onCommand(GetState.class, openShoppingCartCommandHandlers::onGetState)
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

  private EffectBuilder<State> onGet(State state, Get cmd) {
    cmd.replyTo.tell(state.toSummary());
    return Effect().none();
  }

  private class OpenShoppingCartCommandHandlers {

    EffectBuilder<State> onAddItem(State state, AddItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        cmd.replyTo.tell(StatusReply.error(
                "Item '" + cmd.itemId + "' was already added to this shopping cart"));
        return Effect().none();
      } else if (cmd.quantity <= 0) {
        cmd.replyTo.tell(StatusReply.error("Quantity must be greater than zero"));
        return Effect().none();
      } else {
        return Effect().persist(state.updateItem(cmd.itemId, cmd.quantity))
                .thenRun(updatedCart -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary())));
      }
    }

    EffectBuilder<State> onRemoveItem(State state, RemoveItem cmd) {
      if (state.hasItem(cmd.itemId)) {
        return Effect().persist(state.removeItem(cmd.itemId))
                .thenRun(updatedCart -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary())));
      } else {
        cmd.replyTo.tell(StatusReply.success(state.toSummary()));
        return Effect().none();
      }
    }

    EffectBuilder<State> onGetState(State state, GetState cmd) {
      cmd.replyTo.tell(StatusReply.success(state.toSummary()));
      return Effect().none();
    }

    EffectBuilder<State> onMutateState(State state, MutateState cmd) {

      // uncomment this to make mutation to the state (NOT recommended at all)
      state = emptyState();

      state.setIsThat(true);
      return Effect().none();
    }

    EffectBuilder<State> onAdjustItemQuantity(State state, AdjustItemQuantity cmd) {
      if (cmd.quantity <= 0) {
        cmd.replyTo.tell(StatusReply.error("Quantity must be greater than zero"));
        return Effect().none();
      } else if (state.hasItem(cmd.itemId)) {
        return Effect().persist(state.updateItem(cmd.itemId, cmd.quantity))
                .thenRun(updatedCart -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary())));
      } else {
        cmd.replyTo.tell(StatusReply.error(
                "Cannot adjust quantity for item '" + cmd.itemId + "'. Item not present on cart"));
        return Effect().none();
      }
    }

    EffectBuilder<State> onCheckout(State state, Checkout cmd) {
      if (state.isEmpty()) {
        cmd.replyTo.tell(StatusReply.error("Cannot checkout an empty shopping cart"));
        return Effect().none();
      } else {
        return Effect().persist(state.checkout(Instant.now()))
                .thenRun(updatedCart -> cmd.replyTo.tell(StatusReply.success(updatedCart.toSummary())));
      }
    }
  }

  private class CheckedOutCommandHandlers {
    EffectBuilder<State> onAddItem(AddItem cmd) {
      cmd.replyTo.tell(StatusReply.error("Can't add an item to an already checked out shopping cart"));
      return Effect().none();
    }

    EffectBuilder<State> onRemoveItem(RemoveItem cmd) {
      cmd.replyTo.tell(StatusReply.error("Can't remove an item from an already checked out shopping cart"));
      return Effect().none();
    }

    EffectBuilder<State> onAdjustItemQuantity(AdjustItemQuantity cmd) {
      cmd.replyTo.tell(StatusReply.error("Can't adjust item on an already checked out shopping cart"));
      return Effect().none();
    }

    EffectBuilder<State> onCheckout(Checkout cmd) {
      cmd.replyTo.tell(StatusReply.error("Can't checkout already checked out shopping cart"));
      return Effect().none();
    }
  }

}
