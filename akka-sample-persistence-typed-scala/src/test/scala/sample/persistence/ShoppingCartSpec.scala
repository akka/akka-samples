package sample.persistence

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest._

class AsyncTestingExampleSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  val testKit = ActorTestKit()

  "The Shopping Cart" should {
    "keep its state" in {
      val actor = testKit.spawn(ShoppingCart.behavior("myTestPersistenceId"))
      val probe = testKit.createTestProbe[ShoppingCart.Result]
      actor ! ShoppingCart.UpdateItem("foo", 42, probe.ref)
      probe.expectMessage(ShoppingCart.OK)

      testKit.stop(actor)

      val stateProbe = testKit.createTestProbe[ShoppingCart.State]
      val restartedActor = testKit.spawn(ShoppingCart.behavior("myTestPersistenceId"))
      restartedActor ! ShoppingCart.Get(stateProbe.ref)
      stateProbe.receiveMessage.items.size should be(1)
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
