package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.Directives.*;

public class ShoppingCartRoutes {

  public static class AddItem {
    public final String cartId;
    public final String itemId;
    public final int quantity;

    @JsonCreator
    public AddItem(@JsonProperty("cartId") String cartId, @JsonProperty("itemId") String itemId, @JsonProperty("quantity") int quantity) {
      this.cartId = cartId;
      this.itemId = itemId;
      this.quantity = quantity;
    }
  }

  public static class UpdateItem {
    public final String cartId;
    public final String itemId;
    public final int quantity;

    @JsonCreator
    public UpdateItem(@JsonProperty("cartId") String cartId, @JsonProperty("itemId") String itemId, @JsonProperty("quantity") int quantity) {
      this.cartId = cartId;
      this.itemId = itemId;
      this.quantity = quantity;
    }
  }

  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration timeout;

  public ShoppingCartRoutes(ActorSystem<?> system) {
    this.system = system;
    sharding = ClusterSharding.get(system);
    timeout = system.settings().config().getDuration("shopping.askTimeout");
  }


  public Route shopping() {
    return pathPrefix("shopping", () ->
      pathPrefix("carts", () ->
        concat(
          pathPrefix(PathMatchers.segment(), (String cartId) ->
            concat(
              get(() -> handleGetCart(cartId)),
              path("checkout", () ->
                post(() -> handleCheckoutCart(cartId))
              )
            )
          ),
          post(() ->
              entity(
                Jackson.unmarshaller(AddItem.class),
                this::handleAddItem)),
          put(() ->
            entity(
              Jackson.unmarshaller(UpdateItem.class),
              this::handleUpdateItem))
        )
      )
    );
  }

  private Route handleAddItem(AddItem data) {
    System.out.println("handleAddItem");
    EntityRef<ShoppingCart.Command> entityRef =
      sharding.entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, data.cartId);
    CompletionStage<ShoppingCart.Confirmation> reply =
      entityRef.ask(replyTo -> new ShoppingCart.AddItem(data.itemId, data.quantity, replyTo), timeout);
    return onConfirmationReply(reply);
  }

  private Route handleUpdateItem(UpdateItem data) {
    EntityRef<ShoppingCart.Command> entityRef =
      sharding.entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, data.cartId);
    CompletionStage<ShoppingCart.Confirmation> reply;
    if (data.quantity == 0)
      reply = entityRef.ask(replyTo -> new ShoppingCart.RemoveItem(data.itemId, replyTo), timeout);
    else
      reply = entityRef.ask(replyTo -> new ShoppingCart.AdjustItemQuantity(data.itemId, data.quantity, replyTo), timeout);
    return onConfirmationReply(reply);
  }

  private Route onConfirmationReply(CompletionStage<ShoppingCart.Confirmation> reply) {
    return onSuccess(reply, confirmation -> {
      if (confirmation instanceof ShoppingCart.Accepted)
        return complete(StatusCodes.OK, ((ShoppingCart.Accepted) confirmation).summary, Jackson.marshaller());
      else
        return complete(StatusCodes.BAD_REQUEST, ((ShoppingCart.Rejected) confirmation).reason);
    });
  }

  private Route handleGetCart(String cartId) {
    EntityRef<ShoppingCart.Command> entityRef =
      sharding.entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, cartId);
    CompletionStage<ShoppingCart.Summary> reply =
      entityRef.ask(ShoppingCart.Get::new, timeout);
    return onSuccess(reply, summary -> {
      if (summary.items.isEmpty())
        return complete(StatusCodes.NOT_FOUND);
      else
        return complete(StatusCodes.OK, summary, Jackson.marshaller());
    });
  }

  private Route handleCheckoutCart(String cartId) {
    EntityRef<ShoppingCart.Command> entityRef =
      sharding.entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, cartId);
    CompletionStage<ShoppingCart.Confirmation> reply =
      entityRef.ask(ShoppingCart.Checkout::new, timeout);
    return onConfirmationReply(reply);
  }


}
