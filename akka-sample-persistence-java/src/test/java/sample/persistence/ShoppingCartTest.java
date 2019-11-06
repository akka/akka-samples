package sample.persistence;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
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
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId()));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    ShoppingCart.Accepted result = probe.expectMessageClass(ShoppingCart.Accepted.class);
    assertEquals(42, result.summary.items.get("foo").intValue());
    assertFalse(result.summary.checkedOut);
  }

  @Test
  public void shouldRejectAlreadyAddedItem() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId()));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    cart.tell(new ShoppingCart.AddItem("foo", 13, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Rejected.class);
  }

  @Test
  public void shouldRemoveItem() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId()));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    cart.tell(new ShoppingCart.RemoveItem("foo", probe.getRef()));
    ShoppingCart.Accepted result = probe.expectMessageClass(ShoppingCart.Accepted.class);
    assertTrue(result.summary.items.isEmpty());
  }

  @Test
  public void shouldAjustQuantity() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId()));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    cart.tell(new ShoppingCart.AdjustItemQuantity("foo", 43, probe.getRef()));
    ShoppingCart.Accepted result = probe.expectMessageClass(ShoppingCart.Accepted.class);
    assertEquals(43, result.summary.items.get("foo").intValue());
  }

  @Test
  public void shouldCheckout() {
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(newCartId()));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    cart.tell(new ShoppingCart.Checkout(probe.getRef()));
    ShoppingCart.Accepted result = probe.expectMessageClass(ShoppingCart.Accepted.class);
    assertTrue(result.summary.checkedOut);

    cart.tell(new ShoppingCart.AddItem("bar", 13, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Rejected.class);
  }

  @Test
  public void shouldKeepItsState() {
    String cartId = newCartId();
    ActorRef<ShoppingCart.Command> cart = testKit.spawn(ShoppingCart.create(cartId));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    ShoppingCart.Accepted result = probe.expectMessageClass(ShoppingCart.Accepted.class);
    assertEquals(42, result.summary.items.get("foo").intValue());

    testKit.stop(cart);

    // start again with same cartId
    ActorRef<ShoppingCart.Command> restartedCart =
      testKit.spawn(ShoppingCart.create(cartId));
    TestProbe<ShoppingCart.Summary> stateProbe = testKit.createTestProbe();
    restartedCart.tell(new ShoppingCart.Get(stateProbe.getRef()));
    ShoppingCart.Summary state = stateProbe.receiveMessage();
    assertEquals(42, state.items.get("foo").intValue());
  }


}
