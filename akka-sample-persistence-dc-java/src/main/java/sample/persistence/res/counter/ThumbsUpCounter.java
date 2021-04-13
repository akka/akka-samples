package sample.persistence.res.counter;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ReplicatedEntityProvider;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.typed.ReplicationId;
import akka.persistence.typed.javadsl.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import sample.persistence.res.CborSerializable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static sample.persistence.res.MainApp.ALL_REPLICAS;

public final class ThumbsUpCounter extends ReplicatedEventSourcedBehavior<ThumbsUpCounter.Command, ThumbsUpCounter.Event, ThumbsUpCounter.State> {
   
    private final ActorContext<Command> ctx;

    private static Behavior<Command> create(ReplicationId id) {
        return Behaviors.setup(ctx -> ReplicatedEventSourcing.commonJournalConfig(id, ALL_REPLICAS, CassandraReadJournal.Identifier(), replicationContext -> new ThumbsUpCounter(replicationContext, ctx)));
    }

    public static ReplicatedEntityProvider<Command> provider() {
        return ReplicatedEntityProvider.createPerDataCenter(Command.class, "counter", ALL_REPLICAS, ThumbsUpCounter::create);
    }

    private ThumbsUpCounter(ReplicationContext replicationContext, ActorContext<Command> ctx) {
        super(replicationContext);
        this.ctx = ctx;
    }

    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder().forAnyState()
                .onCommand(GiveThumbsUp.class, (state, cmd) -> Effect().persist(new GaveThumbsUp(cmd.userId)).thenRun(state2 -> {
                    ctx.getLog().info("Thumbs-up by {}, total count {}", cmd.userId, state2.users.size());
                    cmd.replyTo.tell((long) state2.users.size());
                })).onCommand(GetCount.class, (state, cmd) -> {
                    cmd.replyTo.tell((long) state.users.size());
                    return Effect().none();
                }).onCommand(GetUsers.class, (state, cmd) -> {
                    cmd.replyTo.tell(state);
                    return Effect().none();
                }).build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder().forAnyState()
                .onEvent(GaveThumbsUp.class, (state, event) ->
                        state.add(event.userId)
                ).build();

    }

    // Classes for commands, events, and state...

    public interface Command extends CborSerializable {
    }

    public static class GiveThumbsUp implements Command {
        public final String resourceId;
        public final String userId;
        public final ActorRef<Long> replyTo;

        public GiveThumbsUp(String resourceId, String userId, ActorRef<Long> replyTo) {
            this.resourceId = resourceId;
            this.userId = userId;
            this.replyTo = replyTo;
        }
    }

    public static class GetCount implements Command {
        public final String resourceId;
        public final ActorRef<Long> replyTo;

        public GetCount(String resourceId, ActorRef<Long> replyTo) {
            this.resourceId = resourceId;
            this.replyTo = replyTo;
        }
    }

    public static class GetUsers implements Command {
        public final String resourceId;
        public final ActorRef<State> replyTo;

        public GetUsers(String resourceId, ActorRef<State> replyTo) {
            this.resourceId = resourceId;
            this.replyTo = replyTo;
        }
    }


    interface Event extends CborSerializable {
    }

    public static class GaveThumbsUp implements Event {
        public final String userId;

        @JsonCreator
        public GaveThumbsUp(String userId) {
            this.userId = userId;
        }
    }

    public static class State implements CborSerializable {
        public final Set<String> users;

        public State() {
            this.users = Collections.emptySet();
        }

        State(Set<String> users) {
            this.users = Collections.unmodifiableSet(users);
        }

        public State add(String userId) {
            // defensive copy for thread safety
            Set<String> newUsers = new HashSet<>(users);
            newUsers.add(userId);
            return new State(newUsers);
        }
    }

}

