package sample.persistence;

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
    "akka.persistence.state.plugin = \"akka.persistence.testkit.state\" \n" +
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
    TestProbe<StatusReply<ShoppingCart.Summary>> probe = testKit.createTestProbe();
    cart.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    StatusReply<ShoppingCart.Summary> result = probe.receiveMessage();
    assertEquals(42, result.getValue().items.get("foo").intValue());
    assertFalse(result.getValue().checkedOut);
    assertFalse(result.getValue().value);


    cart.tell(new ShoppingCart.MutateState(probe.getRef()));


    cart.tell(new ShoppingCart.GetState(probe.getRef()));
    StatusReply<ShoppingCart.Summary> result3 = probe.receiveMessage();
    assertEquals(42, result3.getValue().items.get("foo").intValue());
    assertFalse(result3.getValue().checkedOut);
    assertFalse(result3.getValue().value);
  }

}
