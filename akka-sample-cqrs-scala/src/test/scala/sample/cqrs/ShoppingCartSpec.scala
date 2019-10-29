package sample.cqrs

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.WordSpecLike

class ShoppingCartSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with WordSpecLike {

  private var counter = 0
  def newCartId(): String = {
    counter += 1
    s"cart-$counter"
  }

  "The Shopping Cart" should {

    "add item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessage(ShoppingCart.Accepted(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))
    }

    "reject already added item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      cart ! ShoppingCart.AddItem("foo", 13, probe.ref)
      probe.expectMessageType[ShoppingCart.Rejected]
    }

    "remove item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      cart ! ShoppingCart.RemoveItem("foo", probe.ref)
      probe.expectMessage(ShoppingCart.Accepted(ShoppingCart.Summary(Map.empty, checkedOut = false)))
    }

    "adjust quantity" in {
      val cart = testKit.spawn(ShoppingCart(newCartId(), Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessageType[ShoppingCart.Accepted]
      cart ! ShoppingCart.AdjustItemQuantity("foo", 43, probe.ref)
      probe.expectMessage(ShoppingCart.Accepted(ShoppingCart.Summary(Map("foo" -> 43), checkedOut = false)))
    }

    "keep its state" in {
      val cartId = newCartId()
      val cart = testKit.spawn(ShoppingCart(cartId, Set.empty))
      val probe = testKit.createTestProbe[ShoppingCart.Confirmation]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessage(ShoppingCart.Accepted(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))

      testKit.stop(cart)

      val stateProbe = testKit.createTestProbe[ShoppingCart.Summary]
      val restartedCart1 = testKit.spawn(ShoppingCart(cartId, Set.empty))
      restartedCart1 ! ShoppingCart.Get(stateProbe.ref)
      stateProbe.expectMessage(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false))
    }
  }

}
