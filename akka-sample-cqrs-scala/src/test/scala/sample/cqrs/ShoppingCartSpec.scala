package sample.cqrs

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class ShoppingCartSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with WordSpecLike {

  "The Shopping Cart" should {
    "keep its state" in {
      val cart1 = testKit.spawn(ShoppingCart("cart-1", Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Result]
      cart1 ! ShoppingCart.UpdateItem("foo", 42, probe.ref)
      probe.expectMessage(ShoppingCart.OK)

      testKit.stop(cart1)

      val stateProbe = testKit.createTestProbe[ShoppingCart.State]
      val restartedCart1 = testKit.spawn(ShoppingCart("cart-1", Set.empty))
      restartedCart1 ! ShoppingCart.Get(stateProbe.ref)
      stateProbe.receiveMessage.items.size should be(1)
    }
  }

}
