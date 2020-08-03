package sample.cqrs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.eventstream.EventStream;
import akka.pattern.StatusReply;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import akka.projection.testkit.javadsl.ProjectionTestKit;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ProjectionTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(
            ConfigFactory.parseString(
                    "akka.actor.provider = local \n" +
                    "akka.persistence.cassandra { \n" +
                    "  events-by-tag { \n" +
                    "    eventual-consistency-delay = 200ms \n" +
                    "  } \n" +
                    "  query { \n" +
                    "    refresh-interval = 500 ms \n" +
                    "  } \n" +
                    "  journal.keyspace-autocreate = on \n" +
                    "  journal.tables-autocreate = on \n" +
                    "  snapshot.keyspace-autocreate = on \n" +
                    "  snapshot.tables-autocreate = on \n" +
                    "} \n" +
                    "datastax-java-driver { \n" +
                    "  basic.contact-points = [\"127.0.0.1:19042\"] \n" +
                    "  basic.load-balancing-policy.local-datacenter = datacenter1 \n" +
                    "} \n" +
                    "akka.actor.testkit.typed.single-expect-default = 5s \n" +
                    "# For LoggingTestKit \n" +
                    "akka.actor.testkit.typed.filter-leeway = 5s \n")
                    .withFallback(ConfigFactory.load())
    );

    private static final File databaseDirectory = new File("target/cassandra-EventProcessorTest");

    private static final ProjectionTestKit projectionTestKit = ProjectionTestKit.create(testKit.testKit());

    private final EventProcessorSettings settings = EventProcessorSettings.create(testKit.system());

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
    public void projectionShouldPublishEventsToSystemEventStream() {
        TestProbe<StatusReply<ShoppingCart.Summary>> cartProbe = testKit.createTestProbe();
        ActorRef<ShoppingCart.Command> cart =
                testKit.spawn(ShoppingCart.create("cart-1", Collections.singleton(settings.tagPrefix + "-0")));
        cart.tell(new ShoppingCart.AddItem("25", 12, cartProbe.ref().narrow()));
        assertTrue(cartProbe.receiveMessage().isSuccess());

        TestProbe<ShoppingCart.Event> eventProbe = testKit.createTestProbe();
        testKit.system().eventStream().tell(new EventStream.Subscribe(ShoppingCart.Event.class, eventProbe.ref()));
        projectionTestKit.run(Guardian.createProjectionFor(testKit.system(), settings, 0), () -> {
            ShoppingCart.ItemAdded added = eventProbe.expectMessageClass(ShoppingCart.ItemAdded.class);
            assertEquals("cart-1", added.cartId);
            assertEquals("25", added.itemId);
            assertEquals(12, added.quantity);
        });
    }
}