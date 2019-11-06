package sample.cqrs;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.eventstream.EventStream;
import akka.cluster.MemberStatus;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

// order of test methods matter in this test
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntegrationTest {

  private static final Config config =
    ConfigFactory.parseString(
      "akka.cluster { \n" +
      "  seed-nodes = [] \n" +
      "} \n" +
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
      .withFallback(ConfigFactory.load());

  // one TestKit (ActorSystem) per cluster node
  @ClassRule
  public static final TestKitJunitResource testKit1 = new TestKitJunitResource(
    roleConfig("write-model").withFallback(IntegrationTest.config));

  @ClassRule
  public static final TestKitJunitResource testKit2 = new TestKitJunitResource(
    roleConfig("write-model").withFallback(IntegrationTest.config));

  @ClassRule
  public static final TestKitJunitResource testKit3 = new TestKitJunitResource(
    roleConfig("read-model").withFallback(IntegrationTest.config));

  @ClassRule
  public static final TestKitJunitResource testKit4 = new TestKitJunitResource(
    roleConfig("read-model").withFallback(IntegrationTest.config));

  private static Config roleConfig(String role) {
    return ConfigFactory.parseString("akka.cluster.roles = [" + role + "]");
  }

  private final static File databaseDirectory = new File("target/cassandra-IntegrationTest");

  @BeforeClass
  public static void beforeAll() {
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource(),
      true,
      19042, // default is 9042, but use different for test
      CassandraLauncher.classpathForResources("logback-test.xml"));

    Main.createTables(testKit1.system());
  }

  @AfterClass
  public static void afterAll() throws Exception {
    // The ActorSystem is terminated by the TestKitJunitResource ClassRule, but here it's
    // better to terminate it before Cassandra to avoid error logging.
    ActorTestKit.shutdown(testKit1.system());
    ActorTestKit.shutdown(testKit2.system());
    ActorTestKit.shutdown(testKit3.system());
    ActorTestKit.shutdown(testKit4.system());

    CassandraLauncher.stop();
    FileUtils.deleteDirectory(databaseDirectory);
  }

  private List<ActorSystem<?>> systems3 = Arrays.asList(testKit1.system(), testKit2.system(), testKit3.system());


  @Test
  public void step01_shouldInitAndJoinCluster() {
    testKit1.spawn(Guardian.create(), "guardian");
    testKit2.spawn(Guardian.create(), "guardian");
    testKit3.spawn(Guardian.create(), "guardian");
    // node4 is initialized and joining later

    systems3.forEach(sys ->
      Cluster.get(sys).manager().tell(new Join(Cluster.get(testKit1.system()).selfMember().address()))
    );

    // let the nodes join and become Up
    testKit1.createTestProbe().awaitAssert(Duration.ofSeconds(10), () -> {
      systems3.forEach(sys ->
          assertEquals(MemberStatus.up(), Cluster.get(sys).selfMember().status())
      );
      return null;
    });
  }

  @Test
  public void step02_shouldUpdateAndConsumeFromDifferentNodes() {
    EntityRef<ShoppingCart.Command> cart1 = ClusterSharding.get(testKit1.system())
      .entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, "cart-1");
    TestProbe<ShoppingCart.Confirmation> probe1 = testKit1.createTestProbe();

    EntityRef<ShoppingCart.Command> cart2 = ClusterSharding.get(testKit2.system())
      .entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, "cart-2");
    TestProbe<ShoppingCart.Confirmation> probe2 = testKit2.createTestProbe();

    TestProbe<ShoppingCart.Event> eventProbe3 = testKit3.createTestProbe();
    testKit3.system().eventStream().tell(new EventStream.Subscribe<>(ShoppingCart.Event.class, eventProbe3.getRef()));

    // update from node1, consume event from node3
    cart1.tell(new ShoppingCart.AddItem("foo", 42, probe1.getRef()));
    probe1.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemAdded event1 = eventProbe3.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("cart-1", event1.cartId);
    assertEquals("foo", event1.itemId);
    assertEquals(42, event1.quantity);

    // update from node2, consume event from node3
    cart2.tell(new ShoppingCart.AddItem("bar", 17, probe2.getRef()));
    probe2.expectMessageClass(ShoppingCart.Accepted.class);
    cart2.tell(new ShoppingCart.AdjustItemQuantity("bar", 18, probe2.getRef()));
    probe2.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemAdded event2 = eventProbe3.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("bar", event2.itemId);
    assertEquals(17, event2.quantity);
    ShoppingCart.ItemQuantityAdjusted event3 = eventProbe3.expectMessageClass(ShoppingCart.ItemQuantityAdjusted.class);
    assertEquals("bar", event3.itemId);
    assertEquals(18, event3.quantity);
  }

  @Test
  public void step03_shouldContinueEventProcessingFromOffset() {
    ActorTestKit.shutdown(testKit3.system());

    testKit4.spawn(Guardian.create(), "guardian");

    Cluster.get(testKit4.system()).manager().tell(new Join(Cluster.get(testKit1.system()).selfMember().address()));

    // let the nodes join and become Up
    testKit4.createTestProbe().awaitAssert(Duration.ofSeconds(10), () -> {
      assertEquals(MemberStatus.up(), Cluster.get(testKit4.system()).selfMember().status());
      return null;
    });

    EntityRef<ShoppingCart.Command> cart3 = ClusterSharding.get(testKit1.system())
      .entityRefFor(ShoppingCart.ENTITY_TYPE_KEY, "cart-3");
    TestProbe<ShoppingCart.Confirmation> probe3 = testKit1.createTestProbe();

    TestProbe<ShoppingCart.Event> eventProbe4 = testKit4.createTestProbe();
    testKit4.system().eventStream().tell(new EventStream.Subscribe<>(ShoppingCart.Event.class, eventProbe4.getRef()));

    // update from node1, consume event from node4
    cart3.tell(new ShoppingCart.AddItem("abc", 43, probe3.getRef()));
    probe3.expectMessageClass(ShoppingCart.Accepted.class);
    ShoppingCart.ItemAdded event4 = eventProbe4.expectMessageClass(ShoppingCart.ItemAdded.class);
    assertEquals("cart-3", event4.cartId);
    assertEquals("abc", event4.itemId);
    assertEquals(43, event4.quantity);
  }

}
