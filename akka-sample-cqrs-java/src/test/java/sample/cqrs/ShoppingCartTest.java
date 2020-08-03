package sample.cqrs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.pattern.StatusReply;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShoppingCartTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(
    "akka.persistence.journal.plugin = \"akka.persistence.journal.inmem\" \n" +
      "akka.persistence.snapshot-store.plugin = \"akka.persistence.snapshot-store.local\"  \n" +
      "akka.persistence.snapshot-store.local.dir = \"target/snapshot-" + UUID.randomUUID().toString() + "\"  \n"
  );

  private static AtomicInteger counter = new AtomicInteger();
  private static String newCartId() {
    return "cart-" + counter.incrementAndGet();
  }

  @Test
  public void shouldAddItem() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId(), Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertTrue(result.isSuccess());
    assertEquals(42, result.getValue().items.get("foo").intValue());
    assertFalse(result.getValue().checkedOut);
  }

  @Test
  public void shouldRejectAlreadyAddedItem() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId(), Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    assertTrue(probe.receiveMessage().isSuccess());
    cart.tell(new ShoppingCart.AddItem("foo", 13, probe.getRef()));
    assertTrue(probe.receiveMessage().isError());
  }

  @Test
  public void shouldRemoveItem() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId(), Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    assertTrue(probe.receiveMessage().isSuccess());
    cart.tell(new ShoppingCart.RemoveItem("foo", probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertTrue(result.isSuccess());
    assertTrue(result.getValue().items.isEmpty());
  }

  @Test
  public void shouldAjustQuantity() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId(), Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    assertTrue(probe.receiveMessage().isSuccess());
    cart.tell(new ShoppingCart.AdjustItemQuantity("foo", 43, probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertTrue(result.isSuccess());
    assertEquals(43, result.getValue().items.get("foo").intValue());
  }

  @Test
  public void shouldCheckout() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId(), Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    assertTrue(probe.receiveMessage().isSuccess());
    cart.tell(new ShoppingCart.Checkout(probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertTrue(result.isSuccess());
    assertTrue(result.getValue().checkedOut);

    cart.tell(new ShoppingCart.AddItem("bar", 13, probe.getRef()));
    assertTrue(probe.receiveMessage().isError());
  }

  @Test
  public void shouldKeepItsState() {
    String cartId = newCartId();
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(cartId, Collections.emptySet()));
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertTrue(result.isSuccess());
    assertEquals(42, result.getValue().items.get("foo").intValue());

    testKit.stop(cart);

    // start again with same cartId
    ActorRef<ShoppingCart.Command> restartedCart =
      testKit.spawn(ShoppingCart.create(cartId, Collections.emptySet()));
    TestProbe<ShoppingCart.Summary> stateProbe = testKit.createTestProbe();
    restartedCart.tell(new ShoppingCart.Get(stateProbe.getRef()));
    ShoppingCart.Summary state = stateProbe.receiveMessage();
    assertEquals(42, state.items.get("foo").intValue());
  }


}
