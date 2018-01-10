package sample.persistence.multidc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import akka.persistence.multidc.javadsl.CommandHandler;
import akka.persistence.multidc.javadsl.EventHandler;
import akka.persistence.multidc.javadsl.ReplicatedEntity;

public class ThumbsUpCounter
    extends ReplicatedEntity<ThumbsUpCounter.Command, ThumbsUpCounter.Event, ThumbsUpCounter.State> {

  @Override
  public State initialState() {
    return new State();
  }

  @Override
  public CommandHandler<Command, Event, State> commandHandler() {
    return commandHandlerBuilder(Command.class)
        .matchCommand(GiveThumbsUp.class, (ctx, state, cmd) -> {
          return Effect().persist(new GaveThumbsUp(cmd.userId));
        }).matchCommand(GetCount.class, (ctx, state, cmd) -> {
          ctx.getSender().tell(state.users.size(), ctx.getSelf());
          return Effect().none();
        }).matchCommand(GetUsers.class, (ctx, state, cmd) -> {
          ctx.getSender().tell(state, ctx.getSelf());
          return Effect().none();
        }).build();
  }


  @Override
  public EventHandler<Event, State> eventHandler() {
    return eventHandlerBuilder(Event.class)
        .matchEvent(GaveThumbsUp.class, (state, event) ->
          state.add(event.userId)
        ).build();
  }

  // Classes for commands, events, and state...

  interface Command {}

  public static class GiveThumbsUp implements Command {
    public final String resourceId;
    public final String userId;

    public GiveThumbsUp(String resourceId, String userId) {
      this.resourceId = resourceId;
      this.userId = userId;
    }
  }

  public static class GetCount implements Command {
    public final String resourceId;

    public GetCount(String resourceId) {
      this.resourceId = resourceId;
    }
  }

  public static class GetUsers implements Command {
    public final String resourceId;

    public GetUsers(String resourceId) {
      this.resourceId = resourceId;
    }
  }


  interface Event {}

  public static class GaveThumbsUp implements Event {
    public final String userId;

    public GaveThumbsUp(String userId) {
      this.userId = userId;
    }
  }

  public static class State {
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
