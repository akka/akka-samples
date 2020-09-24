package sample.persistence.res.auction

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.{ReplicaId, ReplicationId}
import akka.persistence.typed.scaladsl.ReplicatedEventSourcing
import sample.persistence.res.CborSerializable

object Auction {

  type MoneyAmount = Int

  case class Bid(bidder: String, offer: MoneyAmount, timestamp: Instant, originReplica: ReplicaId)

  sealed trait Command extends CborSerializable
  case object Finish extends Command // A timer needs to schedule this event at each replica
  final case class OfferBid(bidder: String, offer: MoneyAmount) extends Command
  final case class GetHighestBid(replyTo: ActorRef[Bid]) extends Command
  final case class IsClosed(replyTo: ActorRef[Boolean]) extends Command
  private final case object Close extends Command // Internal, should not be sent from the outside

  sealed trait Event extends CborSerializable
  final case class BidRegistered(bid: Bid) extends Event
  final case class AuctionFinished(atReplica: ReplicaId) extends Event
  final case class WinnerDecided(atReplica: ReplicaId, winningBid: Bid, highestCounterOffer: MoneyAmount)
    extends Event

  /**
   * The auction passes through several workflow phases.
   * First, in `Running` `OfferBid` commands are accepted.
   *
   * `Auction` instances in all DCs schedule a `Finish` command
   * at a given time. That persists the `AuctionFinished` event and the
   * phase is in `Closing` until the auction is finished in all DCs.
   *
   * When the auction has been finished no more `OfferBid` commands are accepted.
   *
   * The auction is also finished immediately if `AuctionFinished` event from another
   * DC is seen before the scheduled `Finish` command. In that way the auction is finished
   * as quickly as possible in all DCs even though there might be some clock skew.
   *
   * One DC is responsible for finally deciding the winner and publishing the result.
   * All events must be collected from all DC before that can happen.
   * When the responsible DC has seen all `AuctionFinished` events from other DCs
   * all other events have also been propagated and it can persist `WinnerDecided` and
   * the auction is finally `Closed`.
   *
   */
  sealed trait AuctionPhase
  case object Running extends AuctionPhase
  final case class Closing(finishedAtReplica: Set[ReplicaId]) extends AuctionPhase
  case object Closed extends AuctionPhase
  //#phase

  //#state
  case class AuctionState(phase: AuctionPhase, highestBid: Bid, highestCounterOffer: MoneyAmount)
    extends CborSerializable {

    def applyEvent(event: Event): AuctionState =
      event match {
        case BidRegistered(b) =>
          if (isHigherBid(b, highestBid))
            withNewHighestBid(b)
          else
            withTooLowBid(b)
        case AuctionFinished(atDc) =>
          phase match {
            case Running =>
              copy(phase = Closing(Set(atDc)))
            case Closing(alreadyFinishedDcs) =>
              copy(phase = Closing(alreadyFinishedDcs + atDc))
            case _ =>
              this
          }
        case _: WinnerDecided =>
          copy(phase = Closed)
      }

    def withNewHighestBid(bid: Bid): AuctionState = {
      require(phase != Closed)
      require(isHigherBid(bid, highestBid))
      copy(highestBid = bid, highestCounterOffer = highestBid.offer // keep last highest bid around
      )
    }

    def withTooLowBid(bid: Bid): AuctionState = {
      require(phase != Closed)
      require(isHigherBid(highestBid, bid))
      copy(highestCounterOffer = highestCounterOffer.max(bid.offer)) // update highest counter offer
    }

    def isHigherBid(first: Bid, second: Bid): Boolean =
      first.offer > second.offer ||
        (first.offer == second.offer && first.timestamp.isBefore(second.timestamp)) || // if equal, first one wins
        // If timestamps are equal, choose by dc where the offer was submitted
        // In real auctions, this last comparison should be deterministic but unpredictable, so that submitting to a
        // particular DC would not be an advantage.
        (first.offer == second.offer && first.timestamp.equals(second.timestamp) && first.originReplica.id
          .compareTo(second.originReplica.id) < 0)
  }

  def apply(
             replica: ReplicaId,
             name: String,
             initialBid: Auction.Bid, // the initial bid is basically the minimum price bidden at start time by the owner
             closingAt: Instant,
             responsibleForClosing: Boolean): Behavior[Command] = Behaviors.setup[Command] { ctx =>
    Behaviors.withTimers { timers =>
      ReplicatedEventSourcing.commonJournalConfig(
        ReplicationId("auction", name, replica),
        allReplicas,
        PersistenceTestKitReadJournal.Identifier) { replicationCtx =>
        new AuctionEntity(ctx, replicationCtx, timers, closingAt, responsibleForClosing, allReplicas)
          .behavior(initialBid)
      }
    }
  }

}
