/**
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.persistence.multidc.auction

import java.time.Instant

import akka.actor.{ ActorRef, Props }
import akka.persistence.multidc.PersistenceMultiDcSettings
import akka.persistence.multidc.scaladsl.ReplicatedEntity

object AuctionEntity {
  type MoneyAmount = Int

  abstract class WallClock {
    def now(): Instant
  }

  case class Bid(bidder: String, offer: MoneyAmount, timestamp: Instant, originDc: String)

  // commands
  sealed trait AuctionCommand
  case class OfferBid(bidder: String, offer: MoneyAmount) extends AuctionCommand
  case object Finish extends AuctionCommand // An auction coordinator needs to schedule this event to each replica
  case object GetHighestBid extends AuctionCommand

  // events
  sealed trait AuctionEvent
  case class BidRegistered(bid: Bid) extends AuctionEvent
  case class AuctionFinished(atDc: String, winningBid: Bid, highestCounterOffer: MoneyAmount) extends AuctionEvent

  def auctionProps(pid: String, auctionSetup: AuctionSetup, clock: WallClock, settings: PersistenceMultiDcSettings): Props =
    ReplicatedEntity.props("auction", Some(pid), () => new AuctionEntity(auctionSetup, clock), settings)

  case class AuctionState(
    stillRunning:        Boolean,
    highestBid:          Bid,
    // in ebay style auctions, we need to keep track of current highest counter offer
    highestCounterOffer: MoneyAmount
  ) {

    def applyEvent(event: AuctionEvent): AuctionState =
      event match {
        case BidRegistered(b) =>
          if (isHigherBid(b, highestBid)) withNewHighestBid(b)
          else withTooLowBid(b)
        case _: AuctionFinished => copy(stillRunning = false)
      }

    def withNewHighestBid(bid: Bid): AuctionState = {
      require(stillRunning)
      require(isHigherBid(bid, highestBid))
      copy(
        highestBid = bid,
        highestCounterOffer = highestBid.offer // keep last highest bid around
      )
    }
    def withTooLowBid(bid: Bid): AuctionState = {
      require(stillRunning)
      require(isHigherBid(highestBid, bid))
      // update highest counter offer
      copy(highestCounterOffer = highestCounterOffer.max(bid.offer))
    }

    def isHigherBid(first: Bid, second: Bid): Boolean =
      first.offer > second.offer ||
        (first.offer == second.offer && first.timestamp.isBefore(second.timestamp)) || // if equal, first one wins
        // If timestamps are equal, choose by dc where the offer was submitted
        // In real auctions, this last comparison should be deterministic but unpredictable, so that submitting to a
        // particular DC would not be an advantage.
        (first.offer == second.offer && first.timestamp.equals(second.timestamp) && first.originDc.compareTo(second.originDc) < 0)
  }

  case class AuctionSetup(
    name:       String,
    initialBid: Bid // the initial bid is basically the minimum price bidden at start time by the owner
  )
}

import AuctionEntity._

class AuctionEntity(auctionSetup: AuctionSetup, clock: WallClock)
  extends ReplicatedEntity[AuctionCommand, AuctionEvent, AuctionState] {

  override def initialState: AuctionState =
    AuctionState(
      stillRunning = true,
      highestBid = auctionSetup.initialBid,
      highestCounterOffer = auctionSetup.initialBid.offer)

  override def commandHandler: CommandHandler = {
    CommandHandler {
      case (OfferBid(bidder, offer), _, ctx) =>
        Effect.persist(BidRegistered(Bid(bidder, offer, clock.now(), selfDc)))
      case (Finish, state, ctx) =>
        Effect.persist(AuctionFinished(selfDc, state.highestBid, state.highestCounterOffer))
      case (GetHighestBid, state, ctx) =>
        ctx.sender() ! state.highestBid.copy(offer = state.highestCounterOffer)
        Effect.done
    }
  }

  override def applyEvent(event: AuctionEvent, state: AuctionState): AuctionState =
    state.applyEvent(event)
}
