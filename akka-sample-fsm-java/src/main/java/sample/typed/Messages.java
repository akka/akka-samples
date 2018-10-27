package sample.typed;

import akka.actor.typed.ActorRef;

public class Messages {


    interface ChopstickMessage {

        final class Take implements ChopstickMessage {
            public final ActorRef<ChopstickAnswer> hakker;

            public Take(ActorRef<ChopstickAnswer> hakker) {
                this.hakker = hakker;
            }
        }

        final class Put implements ChopstickMessage {
            public final ActorRef<ChopstickAnswer> hakker;

            public Put(ActorRef<ChopstickAnswer> hakker) {
                this.hakker = hakker;
            }
        }
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

        final class Taken implements ChopstickAnswer {
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

        final class Busy implements ChopstickAnswer {
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
    }

    interface HakkerMessage {

        enum Eat implements HakkerMessage {
            INSTANCE
        }

        enum Think implements HakkerMessage {
            INSTANCE
        }
    }

}
