package sample.distributeddata

import scala.concurrent.duration._
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.Replicator.GetReplicaCount
import akka.cluster.ddata.Replicator.ReplicaCount
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit._
import com.typesafe.config.ConfigFactory

object ShoppingORCartSpec extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    """))

}

class ShoppingORCartSpecMultiJvmNode1 extends ShoppingORCartSpec
class ShoppingORCartSpecMultiJvmNode2 extends ShoppingORCartSpec
class ShoppingORCartSpecMultiJvmNode3 extends ShoppingORCartSpec

class ShoppingORCartSpec extends MultiNodeSpec(ShoppingORCartSpec) with STMultiNodeSpec with ImplicitSender {
  import ShoppingORCartSpec._
  import ShoppingORCart._

  override def initialParticipants = roles.size

  val cluster = Cluster(system)
  val shoppingORCart = system.actorOf(ShoppingORCart.props("user-1"))

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }

  "Demo of a replicated shopping cart" must {
    "join cluster" in within(20.seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      awaitAssert {
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }

    "handle updates directly after start" in within(15.seconds) {
      runOn(node2) {
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("1", "Apples", quantity = 2))
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("2", "Oranges", quantity = 3))
      }
      enterBarrier("updates-done")

      awaitAssert {
        shoppingORCart ! ShoppingORCart.GetCart
        val cart = expectMsgType[Cart]
        cart.items should be(Set(LineItem("1", "Apples", quantity = 2), LineItem("2", "Oranges", quantity = 3)))
      }

      enterBarrier("after-2")
    }

    "handle updates from different nodes" in within(5.seconds) {
      runOn(node2) {
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("1", "Apples", quantity = 5))
        shoppingORCart ! ShoppingORCart.RemoveItem("2")
      }
      runOn(node3) {
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("3", "Bananas", quantity = 4))
      }
      enterBarrier("updates-done")

      awaitAssert {
        shoppingORCart ! ShoppingORCart.GetCart
        val cart = expectMsgType[Cart]
        cart.items should be(Set(LineItem("1", "Apples", quantity = 5), LineItem("3", "Bananas", quantity = 4)))
      }

      enterBarrier("after-3")
    }

    "handle more updates from different nodes" in within(5.seconds) {
      runOn(node2) {
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("1", "Apples", quantity = 2))
      }
      runOn(node3) {
        shoppingORCart ! ShoppingORCart.ChangeItemQuantity(LineItem("3", "Bananas", quantity = 6))
      }
      enterBarrier("updates-done")

      awaitAssert {
        shoppingORCart ! ShoppingORCart.GetCart
        val cart = expectMsgType[Cart]
        cart.items should be(Set(LineItem("1", "Apples", quantity = 2), LineItem("3", "Bananas", quantity = 6)))
      }

      enterBarrier("after-3")
    }

  }

}

