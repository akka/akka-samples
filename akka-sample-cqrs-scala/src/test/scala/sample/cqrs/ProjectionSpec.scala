package sample.cqrs

import java.io.File

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.eventstream.EventStream
import akka.pattern.StatusReply
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.projection.testkit.scaladsl.ProjectionTestKit
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

object ProjectionSpec {
  def config =
    ConfigFactory.parseString("""
      akka.actor.provider=local
      akka.persistence.cassandra {		
         events-by-tag {		
           eventual-consistency-delay = 200ms		
         }		
       		
         query {		
           refresh-interval = 500 ms		
         }		
       		
         journal.keyspace-autocreate = on		
         journal.tables-autocreate = on		
         snapshot.keyspace-autocreate = on		
         snapshot.tables-autocreate = on		
       }		
       datastax-java-driver {		
         basic.contact-points = ["127.0.0.1:19042"]		
         basic.load-balancing-policy.local-datacenter = "datacenter1"		
       }
    """).withFallback(ConfigFactory.load()) // re-use application.conf other settings
}

class ProjectionSpec
    extends ScalaTestWithActorTestKit(ProjectionSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterAll {
  val projectionTestKit = ProjectionTestKit(system)
  val settings = EventProcessorSettings(system)

  val databaseDirectory = new File("target/cassandra-ProjectionSpec")

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

    "be published to the system event stream by the projection" in {
      val cartProbe = createTestProbe[Any]()
      val cart = spawn(ShoppingCart("cart-1", Set(s"${settings.tagPrefix}-0")))
      cart ! ShoppingCart.AddItem("25", 12, cartProbe.ref)
      cartProbe.expectMessageType[StatusReply[ShoppingCart.Summary]].isSuccess should ===(true)

      val eventProbe = createTestProbe[ShoppingCart.Event]()
      system.eventStream ! EventStream.Subscribe(eventProbe.ref)
      projectionTestKit.run(Guardian.createProjectionFor(system, settings, 0)) {
        val added = eventProbe.expectMessageType[ShoppingCart.ItemAdded]
        added.cartId should ===("cart-1")
        added.itemId should ===("25")
        added.quantity should ===(12)
      }
    }
  }

}
