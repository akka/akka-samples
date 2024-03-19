/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.persistence.res.bank;


import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.pattern.StatusReply;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.typed.ReplicaId;
import akka.persistence.typed.ReplicationId;
import akka.persistence.typed.javadsl.*;
import sample.persistence.res.CborSerializable;
import sample.persistence.res.MainApp;

public class BankAccount extends ReplicatedEventSourcedBehavior<BankAccount.Command, BankAccount.Event, BankAccount.State> {


    private ActorContext<Command> context;

    public interface Command extends CborSerializable {
    }

    public final static class Deposit implements Command {
        public final long amount;
        public final ActorRef<StatusReply<Done>> replyTo;

        public Deposit(long amount, ActorRef<StatusReply<Done>> replyTo) {
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public final static class Withdraw implements Command {
        public final long amount;
        public final ActorRef<StatusReply<Done>> replyTo;

        public Withdraw(long amount, ActorRef<StatusReply<Done>> replyTo) {
            this.amount = amount;
            this.replyTo = replyTo;
        }
    }

    public final static class GetBalance implements Command {
        public final ActorRef<Long> replyTo;

        public GetBalance(ActorRef<Long> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public final static class AlertOverdrawn implements Command {
        public final long amount;

        public AlertOverdrawn(long amount) {
            this.amount = amount;
        }
    }

    public interface Event extends CborSerializable {
    }

    public final static class Deposited implements Event {
        public final long amount;

        public Deposited(long amount) {
            this.amount = amount;
        }
    }

    public final static class Withdrawn implements Event {
        public final long amount;

        public Withdrawn(long amount) {
            this.amount = amount;
        }
    }

    public final static class Overdrawn implements Event {
        public final long amount;

        public Overdrawn(long amount) {
            this.amount = amount;
        }
    }

    final static class State {
        public final long balance;

        private State(long balance) {
            this.balance = balance;
        }
        public State withdraw(long amount) {
            return new State(balance - amount);
        }
        public State deposit(long amount) {
            return new State(balance + amount);
        }
    }

    public static Behavior<Command> create(ReplicationId replicationId) {
        return Behaviors.setup(context -> ReplicatedEventSourcing.commonJournalConfig(replicationId, MainApp.ALL_REPLICAS, CassandraReadJournal.Identifier(), replicationContext -> new BankAccount(replicationContext, context)));
    }

    public BankAccount(ReplicationContext replicationContext, ActorContext<Command> context) {
        super(replicationContext);
        this.context = context;
    }


    @Override
    public State emptyState() {
        return new State(0);
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Withdraw.class, (State state, Withdraw w) -> {
                    if (state.balance - w.amount > 0) {
                        return Effect().none().thenReply(w.replyTo, s -> StatusReply.error("insufficient funds"));
                    } else {
                        return Effect().persist(new Withdrawn(w.amount)).thenReply(w.replyTo, s -> StatusReply.ack());
                    }
                })
                .onCommand(Deposit.class, d -> Effect().persist(new Deposited(d.amount)).thenReply(d.replyTo, s -> StatusReply.ack()))
                .onCommand(GetBalance.class, gb -> Effect().none().thenReply(gb.replyTo, s -> s.balance))
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
               .forAnyState()
               .onEvent(Deposited.class, (state, deposited) -> state.deposit(deposited.amount))
               .onEvent(Withdrawn.class, (state, withdrawn) -> {
                   State newState = state.withdraw(withdrawn.amount);
                   detectOverdrawn(newState, getReplicationContext(), context);
                   return newState;
               })
               .onEvent(Overdrawn.class, (state, overdrawn) -> state)
        .build();
    }

    /**
     * Here we trigger events based on replicated events
     */
    private void detectOverdrawn(BankAccount.State state, ReplicationContext replicationContext, ActorContext<Command> context) {
        if (
                replicationContext.concurrent() // this event happened concurrently with other events already processed
                        && replicationContext.origin().equals(new ReplicaId("eu-central")) // if we only want to do the side effect in a single DC
                        && !replicationContext.recoveryRunning() // probably want to avoid re-execution of side effects during recovery
        ) {
            // there's a chance we may have gone into the overdraft due to concurrent events due to concurrent requests
            // or a network partition
            if (state.balance < 0) {
                // the trigger could happen here, in a projection, or as done here by sending a command back to self so that an event can be stored
                context.getSelf().tell(new AlertOverdrawn(state.balance));
            }
        }
    }

}
