package sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

public class Chopstick {
    interface ChopstickMessage { }

    final static class Take implements ChopstickMessage {
        public final ActorRef<ChopstickAnswer> hakker;

        public Take(ActorRef<ChopstickAnswer> hakker) {
            this.hakker = hakker;
        }
    }

    final static class Put implements ChopstickMessage {
        public final ActorRef<ChopstickAnswer> hakker;

        public Put(ActorRef<ChopstickAnswer> hakker) {
            this.hakker = hakker;
        }
    }

    public static Behavior<ChopstickMessage> create() {
        return Behaviors.setup(context-> new Chopstick(context).available());
    }

    interface ChopstickAnswer {

        ActorRef<ChopstickMessage> getChopstick();

        default boolean isTakenBy() {
            return false;
        }

        default boolean isTakenBy(ActorRef<ChopstickMessage> chopstick) {
            return false;
        }

        default boolean isBusy() {
            return false;
        }

        default boolean isBusy(ActorRef<ChopstickMessage> chopstick) {
            return false;
        }


    }

    final static class Taken implements ChopstickAnswer {
        public final ActorRef<ChopstickMessage> chopstick;

        public Taken(ActorRef<ChopstickMessage> chopstick) {
            this.chopstick = chopstick;
        }

        @Override
        public ActorRef<ChopstickMessage> getChopstick() {
            return chopstick;
        }

        @Override
        public boolean isTakenBy(ActorRef<ChopstickMessage> chopstick) {
            return this.chopstick.equals(chopstick);
        }

        @Override
        public boolean isTakenBy() {
            return true;
        }
    }

    final static class Busy implements ChopstickAnswer {
        public final ActorRef<ChopstickMessage> chopstick;

        public Busy(ActorRef<ChopstickMessage> chopstick) {
            this.chopstick = chopstick;
        }

        @Override
        public ActorRef<ChopstickMessage> getChopstick() {
            return chopstick;
        }

        @Override
        public boolean isBusy() {
            return true;
        }

        @Override
        public boolean isBusy(ActorRef<ChopstickMessage> chopstick) {
            return this.chopstick.equals(chopstick);
        }
    }

    private final ActorContext<ChopstickMessage> context;

    private Chopstick(ActorContext<ChopstickMessage> context) {
        this.context = context;
    }

    private Behavior<ChopstickMessage> takenBy(ActorRef<ChopstickAnswer> hakker) {
        return Behaviors.receive(ChopstickMessage.class)
                .onMessage(Take.class, (msg) -> {
                    msg.hakker.tell(new Busy(context.getSelf()));
                    return Behaviors.same();
                })
                .onMessage(Put.class, m -> m.hakker.equals(hakker), (msg) -> available())
                .build();

    }

    private Behavior<ChopstickMessage> available() {
        return Behaviors.receive(ChopstickMessage.class)
                .onMessage(Take.class, (msg) -> {
                    msg.hakker.tell(new Taken(context.getSelf()));
                    return takenBy(msg.hakker);
                })
                .build();
    }
}
