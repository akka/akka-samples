package sample.cqrs;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.eventstream.EventStream;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

public class EventProcessorTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(
    ConfigFactory.parseString(
      "akka.actor.provider = local \n" +
      "cassandra-journal { \n" +
      "  port = 19042  \n" +
      "} \n" +
      "cassandra-snapshot-store { \n" +
      "  port = 19042 \n" +
      "} \n" +
      "cassandra-query-journal { \n" +
      "  refresh-interval = 500 ms \n" +
      "  events-by-tag { \n" +
      "    eventual-consistency-delay = 200 ms \n" +
      "  } \n" +
      "} \n" +
      "akka.actor.testkit.typed.single-expect-default = 5s \n" +
      "# For LoggingTestKit \n" +
      "akka.actor.testkit.typed.filter-leeway = 5s \n")
      .withFallback(ConfigFactory.load())
  );

  private final static File databaseDirectory = new File("target/cassandra-EventProcessorTest");

  @BeforeClass
  public static void beforeAll() {
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource(),
      true,
      19042, // default is 9042, but use different for test
      CassandraLauncher.classpathForResources("logback-test.xml"));

    Main.createTables(testKit.system());
  }

  @AfterClass
  public static void afterAll() throws Exception {
    // The ActorSystem is terminated by the TestKitJunitResource ClassRule, but here it's
    // better to terminate it before Cassandra to avoid error logging.
    testKit.system().terminate();
    testKit.system().getWhenTerminated().toCompletableFuture().get(15, TimeUnit.SECONDS);
    CassandraLauncher.stop();
    FileUtils.deleteDirectory(databaseDirectory);
  }

  @Test
  public void shouldConsumeEventsFromShoppingCart() {
    ActorSystem<Void> system = testKit.system();
    ActorRef<ShoppingCart.Command> cart1 =
      testKit.spawn(ShoppingCart.create("cart-1", Collections.singleton("carts-slice-0")));
    TestProbe<ShoppingCart.Confirmation> probe = testKit.createTestProbe();

    TestProbe<ShoppingCart.Event> eventProbe = testKit.createTestProbe();
    system.eventStream().tell(new EventStream.Subscribe<>(ShoppingCart.Event.class, eventProbe.getRef()));

    testKit.spawn(
      EventProcessor.create(
        new ShoppingCartEventProcessorStream(system, "EventProcessor", "carts-slice-0")));

    cart1.tell(new ShoppingCart.AddItem("foo", 42, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemAdded event1 = eventProbe.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("cart-1", event1.cartId);
    assertEquals("foo", event1.itemId);
    assertEquals(42, event1.quantity);

    cart1.tell(new ShoppingCart.AddItem("bar", 17, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemAdded event2 = eventProbe.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("bar", event2.itemId);
    assertEquals(17, event2.quantity);

    cart1.tell(new ShoppingCart.AdjustItemQuantity("bar", 18, probe.getRef()));
    probe.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemQuantityAdjusted event3 = eventProbe.expectMessageClass(ShoppingCart.ItemQuantityAdjusted.class);
    assertEquals("bar", event3.itemId);
    assertEquals(18, event3.quantity);

    ActorRef<ShoppingCart.Command> cart2 =
      testKit.spawn(ShoppingCart.create("cart-2", Collections.singleton("carts-slice-0")));

    // also verify that EventProcessor is logging
    LoggingTestKit.info("consumed ItemAdded(cart-2,another,1)").expect(system, () -> {
      cart2.tell(new ShoppingCart.AddItem("another", 1, probe.getRef()));
      probe.expectMessageClass(ShoppingCart.Accepted.class);
      return null;
    });
    ShoppingCart.ItemAdded event4 = eventProbe.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("cart-2", event4.cartId);
    assertEquals("another", event4.itemId);
    assertEquals(1, event4.quantity);

  }

}
