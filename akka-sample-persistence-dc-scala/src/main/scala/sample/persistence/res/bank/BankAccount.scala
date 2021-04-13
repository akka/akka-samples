package sample.persistence.res.bank

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.ReplicatedEntityProvider
import akka.pattern.StatusReply
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.typed.scaladsl.ReplicationContext
import akka.persistence.typed.{ReplicaId, ReplicationId}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplicatedEventSourcing}
import sample.persistence.res.{CborSerializable, MainApp}

object BankAccount {

  sealed trait Command extends CborSerializable
  final case class Deposit(amount: Long, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class Withdraw(amount: Long, replyTo: ActorRef[StatusReply[Done]]) extends Command
  final case class GetBalance(replyTo: ActorRef[Long]) extends Command

  private case class AlertOverdrawn(long: Long) extends Command

  sealed trait Event extends CborSerializable
  final case class Deposited(amount: Long) extends Event
  final case class Withdrawn(amount: Long) extends Event
  final case class Overdrawn(amount: Long) extends Event

  private final case class State(balance: Long) {
    def applyOperation(event: Event): State = event match {
      case Deposited(amount) => State(balance + amount)
      case Withdrawn(amount) => State(balance - amount)
      case Overdrawn(_)      => this
    }
  }


  def apply(replicationId: ReplicationId): Behavior[Command] = {
    Behaviors.setup[Command] { context =>
      ReplicatedEventSourcing.commonJournalConfig(
        replicationId,
        MainApp.AllReplicas,
        CassandraReadJournal.Identifier,
      )(replicationContext => eventSourcedBehavior(replicationContext, context))
    }
  }

  // For sharding
  val Provider: ReplicatedEntityProvider[Command] = ReplicatedEntityProvider
    .perDataCenter("account", MainApp.AllReplicas) { replicationId => BankAccount(replicationId) }

  private def eventSourcedBehavior(replicationContext: ReplicationContext, context: ActorContext[Command]): EventSourcedBehavior[Command, Event, State] =
    EventSourcedBehavior[Command, Event, State](
      replicationContext.persistenceId,
      State(0),
      (state, command) => commandHandler(state, command),
      { (state, event) =>
        val newState = state.applyOperation(event)
        detectOverdrawn(newState, replicationContext, context)
        newState
      }
    )

  private def commandHandler(state: State, command: Command): Effect[Event, State] = command match {
    case Deposit(amount, ack) =>
      Effect.persist(Deposited(amount)).thenRun(_ => ack ! StatusReply.ack())
    case Withdraw(amount, ack) =>
      if (state.balance - amount >= 0) {
       Effect.persist(Withdrawn(amount)).thenRun(_ => ack ! StatusReply.ack())
      } else {
        Effect.none.thenRun(_ => ack ! StatusReply.error("insufficient funds"))
      }
    case GetBalance(replyTo) =>
      Effect.none.thenRun(_ =>replyTo ! state.balance)
    case AlertOverdrawn(amount) =>
      Effect.persist(Overdrawn(amount))
  }

  /**
   * Here we trigger events based on replicated events
   */
  def detectOverdrawn(state: BankAccount.State, replicationContext: ReplicationContext, context: ActorContext[Command]): Unit = {
    if (
      replicationContext.concurrent // this event happened concurrently with other events already processed
      && replicationContext.replicaId == ReplicaId("eu-central") // if we only want to do the side effect in a single DC
      && !replicationContext.recoveryRunning // probably want to avoid re-execution of side effects during recovery
    ) {
      // there's a chance we may have gone into the overdraft due to concurrent events due to concurrent requests
      // or a network partition
      if (state.balance < 0) {
        // the trigger could happen here, in a projection, or as done here by sending a command back to self so that an event can be stored
        context.self ! AlertOverdrawn(state.balance)
      }
    }
  }
}





















