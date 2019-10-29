package sample.cqrs

import java.io.File

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.WordSpecLike

class EventProcessorSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString(s"""
      akka.actor.provider = local
      cassandra-journal {
        port = 19042
      }
      cassandra-snapshot-store {
        port = 19042
      }
      cassandra-query-journal {
        refresh-interval = 500 ms
        events-by-tag {
          eventual-consistency-delay = 200 ms
        }
      }
      # For LoggingTestKit
      akka.actor.testkit.typed.filter-leeway = 5s
    """).withFallback(ConfigFactory.defaultApplication())) with WordSpecLike {

  val databaseDirectory = new File("target/cassandra-EventProcessorSpec")

  override protected def beforeAll(): Unit = {
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource,
      clean = true,
      port = 19042, // default is 9042, but use different for test
      CassandraLauncher.classpathForResources("logback-test.xml"))

    Main.createTables(system)

    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    CassandraLauncher.stop()
    FileUtils.deleteDirectory(databaseDirectory)
  }

  "The events from the Shopping Cart" should {
    "be consumed by the event processor" in {
      val cart1 = testKit.spawn(ShoppingCart("cart-1", Set("tag-0")))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]

      val eventProbe = testKit.createTestProbe[ShoppingCart.Event]()
      testKit.system.eventStream ! EventStream.Subscribe(eventProbe.ref)

      testKit.spawn(
        EventProcessor(
          new ShoppingCartEventProcessorStream(system, system.executionContext, "EventProcessor", "tag-0")))
      cart1 ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      eventProbe.expectMessage(ShoppingCart.ItemAdded("cart-1", "foo", 42))

      cart1 ! ShoppingCart.AddItem("bar", 17, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      eventProbe.expectMessage(ShoppingCart.ItemAdded("cart-1", "bar", 17))
      cart1 ! ShoppingCart.AdjustItemQuantity("bar", 18, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      eventProbe.expectMessage(ShoppingCart.ItemQuantityAdjusted("cart-1", "bar", 18))

      val cart2 = testKit.spawn(ShoppingCart("cart-2", Set("tag-0")))
      // also verify that EventProcessor is logging
      LoggingTestKit.info("consumed ItemAdded(cart-2,another,1)").intercept {
        cart2 ! ShoppingCart.AddItem("another", 1, probe.ref)
        probe.expectMessageType[ShoppingCart.Accepted]
      }
      eventProbe.expectMessage(ShoppingCart.ItemAdded("cart-2", "another", 1))
    }
  }

}
