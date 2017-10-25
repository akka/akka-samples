/**
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.persistence.multidc.auction

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ ActorRef, Props }
import akka.persistence.multidc.PersistenceMultiDcSettings
import akka.persistence.multidc.testkit._

object AuctionExampleSpec {
  import AuctionEntity._

  /** A clock that never shows the same time twice (but always increases by 1 second every time we look) */
  class AlwaysIncreasingWallClock extends WallClock {
    val lastTime: AtomicLong = new AtomicLong(Instant.now().getEpochSecond)
    override def now() = Instant.ofEpochSecond(lastTime.incrementAndGet())
  }

  val clock = new AlwaysIncreasingWallClock()

}

class AuctionExampleSpec extends BaseSpec("AuctionExampleSpec") {
  import AuctionExampleSpec._
  import AuctionEntity._

  class TestSetup(testName: String) {
    val minimumBid = 12
    val auctionSetup = AuctionSetup("bicycle", Bid("me", minimumBid, Instant.now(), ""))

    val nodeA = system.actorOf(auctionProps(s"bikeAuction-$testName", auctionSetup, clock, settings), s"auction-A-$testName")
    val nodeB = otherSystem.actorOf(auctionProps(s"bikeAuction-$testName", auctionSetup, clock, otherSettings), s"auction-B-$testName")

    def expectHighestBid(node: ActorRef): Bid = {
      node ! GetHighestBid
      expectMsgType[Bid]
    }
    def expectHighestBid(node: ActorRef, bidder: String, expected: MoneyAmount): Unit = {
      val bid = expectHighestBid(node)
      bid.offer shouldEqual expected
      bid.bidder shouldEqual bidder
    }
  }

  "AuctionExample" should {
    "propagate highest bid to replicated actor" in new TestSetup("test1") {
      // simple bidding
      nodeA ! OfferBid("Mary", 42)
      expectHighestBid(nodeA, "Mary", minimumBid) // ebay style, still the minimum offer

      nodeA ! OfferBid("Paul", 41)
      expectHighestBid(nodeA, "Mary", 41) // ebay style, now updated to the highest counter offer

      awaitAssert {
        // check that results have propagated to b
        expectHighestBid(nodeB, "Mary", 41) // ebay style, now updated to the highest counter offer
      }

      // make sure that first bidder still keeps the crown
      nodeB ! OfferBid("c", 42)
      expectHighestBid(nodeB, "Mary", 42)
    }

    "eventually resolve conflicting bids during auction if bids are highest (but different) in each dc" in new TestSetup("test2") {
      // highest bid comes first
      nodeA ! OfferBid("Mary", 42)
      nodeB ! OfferBid("Paul", 41)

      awaitAssert {
        expectHighestBid(nodeA, "Mary", 41)
        expectHighestBid(nodeB, "Mary", 41)
      }

      // highest bid comes second
      nodeA ! OfferBid("Paul", 50)
      nodeB ! OfferBid("Kat", 60)

      awaitAssert {
        expectHighestBid(nodeA, "Kat", 50)
        expectHighestBid(nodeB, "Kat", 50)
      }
    }
    "eventually resolve conflicting bids during auction if bids are highest and equal (but different time) in each dc" in new TestSetup("test3") {
      nodeA ! OfferBid("Mary", 15)
      expectHighestBid(nodeA, "Mary", 12)

      nodeB ! OfferBid("Paul", 15)

      awaitAssert {
        expectHighestBid(nodeA, "Mary", 15)
        expectHighestBid(nodeB, "Mary", 15)
      }
    }

    "eventually come to a consistent final result" in {
      pending
    }
  }
}
