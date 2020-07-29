package sample.persistence

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import org.scalatest.wordspec.AnyWordSpecLike

class ShoppingCartSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike {

  private var counter = 0
  def newCartId(): String = {
    counter += 1
    s"cart-$counter"
  }

  "The Shopping Cart" should {

    "add item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId()))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessage(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))
    }

    "reject already added item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId()))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.receiveMessage().isSuccess should === (true)
      cart ! ShoppingCart.AddItem("foo", 13, probe.ref)
      probe.receiveMessage().isError should === (true)
    }

    "remove item" in {
      val cart = testKit.spawn(ShoppingCart(newCartId()))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.receiveMessage().isSuccess should === (true)
      cart ! ShoppingCart.RemoveItem("foo", probe.ref)
      probe.expectMessage(StatusReply.Success(ShoppingCart.Summary(Map.empty, checkedOut = false)))
    }

    "adjust quantity" in {
      val cart = testKit.spawn(ShoppingCart(newCartId()))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.receiveMessage().isSuccess should === (true)
      cart ! ShoppingCart.AdjustItemQuantity("foo", 43, probe.ref)
      probe.expectMessage(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 43), checkedOut = false)))
    }

    "checkout" in {
      val cart = testKit.spawn(ShoppingCart(newCartId()))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.receiveMessage().isSuccess should === (true)
      cart ! ShoppingCart.Checkout(probe.ref)
      probe.expectMessage(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = true)))

      cart ! ShoppingCart.AddItem("bar", 13, probe.ref)
      probe.receiveMessage().isError should === (true)
    }

    "keep its state" in {
      val cartId = newCartId()
      val cart = testKit.spawn(ShoppingCart(cartId))
      val probe = testKit.createTestProbe[StatusReply[ShoppingCart.Summary]]
      cart ! ShoppingCart.AddItem("foo", 42, probe.ref)
      probe.expectMessage(StatusReply.Success(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false)))

      testKit.stop(cart)

      // start again with same cartId
      val restartedCart = testKit.spawn(ShoppingCart(cartId))
      val stateProbe = testKit.createTestProbe[ShoppingCart.Summary]
      restartedCart ! ShoppingCart.Get(stateProbe.ref)
      stateProbe.expectMessage(ShoppingCart.Summary(Map("foo" -> 42), checkedOut = false))
    }
  }

}
