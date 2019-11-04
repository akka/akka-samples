package sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

public class Chopstick {
    interface Command { }

    final static class Take implements Command {
        public final ActorRef<Answer> hakker;

        public Take(ActorRef<Answer> hakker) {
            this.hakker = hakker;
        }
    }

    final static class Put implements Command {
        public final ActorRef<Answer> hakker;

        public Put(ActorRef<Answer> hakker) {
            this.hakker = hakker;
        }
    }


    interface Answer {

        ActorRef<Command> getChopstick();

        default boolean isTaken() {
            return false;
        }

        default boolean isBusy() {
            return false;
        }
    }

    final static class Taken implements Answer {
        public final ActorRef<Command> chopstick;

        public Taken(ActorRef<Command> chopstick) {
            this.chopstick = chopstick;
        }

        @Override
        public ActorRef<Command> getChopstick() {
            return chopstick;
        }

        @Override
        public boolean isTaken() {
            return true;
        }
    }

    final static class Busy implements Answer {
        public final ActorRef<Command> chopstick;

        public Busy(ActorRef<Command> chopstick) {
            this.chopstick = chopstick;
        }

        @Override
        public ActorRef<Command> getChopstick() {
            return chopstick;
        }

        @Override
        public boolean isBusy() {
            return true;
        }

    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context-> new Chopstick(context).available());
    }

    private final ActorContext<Command> context;

    private Chopstick(ActorContext<Command> context) {
        this.context = context;
    }

    private Behavior<Command> takenBy(ActorRef<Answer> hakker) {
        return Behaviors.receive(Command.class)
                .onMessage(Take.class, msg -> {
                    msg.hakker.tell(new Busy(context.getSelf()));
                    return Behaviors.same();
                })
                .onMessage(Put.class, m -> m.hakker.equals(hakker), (msg) -> available())
                .build()    ;

    }

    private Behavior<Command> available() {
        return Behaviors.receive(Command.class)
                .onMessage(Take.class, (msg) -> {
                    msg.hakker.tell(new Taken(context.getSelf()));
                    return takenBy(msg.hakker);
                })
                .build();
    }
}
