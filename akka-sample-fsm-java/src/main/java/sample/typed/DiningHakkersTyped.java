package sample.typed;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static sample.typed.Messages.*;

public class DiningHakkersTyped {

    /*
     * A Chopstick is an actor, it can be taken, and put back
     */
    interface Chopstick {

        static Behavior<ChopstickMessage> takenBy(ActorRef<ChopstickAnswer> hakker) {

            return Behaviors.receive(ChopstickMessage.class)
                    .onMessage(ChopstickMessage.Take.class, (ctx, msg) -> {
                        msg.hakker.tell(new ChopstickAnswer.Busy(ctx.getSelf()));
                        return Behaviors.same();
                    })
                    .onMessage(ChopstickMessage.Put.class, m -> m.hakker.equals(hakker), (ctx, msg) -> available)
                    .build();

        }

        Behavior<ChopstickMessage> available = Behaviors.receive(ChopstickMessage.class)
                .onMessage(ChopstickMessage.Take.class, (ctx, msg) -> {
                    msg.hakker.tell(new ChopstickAnswer.Taken(ctx.getSelf()));
                    return takenBy(msg.hakker);
                })
                .build();
    }

    /*
     * A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
     */
    static class Hakker {

        static class ChopstickAnswerAdaptor implements HakkerMessage {
            final ChopstickAnswer msg;

            public ChopstickAnswerAdaptor(ChopstickAnswer msg) {
                this.msg = msg;
            }
        }

        final String name;
        final ActorRef<ChopstickMessage> left;
        final ActorRef<ChopstickMessage> right;

        public Hakker(String name, ActorRef<ChopstickMessage> left, ActorRef<ChopstickMessage> right) {
            this.name = name;
            this.left = left;
            this.right = right;
        }

        Behavior<HakkerMessage> waiting() {
            return Behaviors.receive(HakkerMessage.class)
                    .onMessage(HakkerMessage.Think.class, (ctx, msg) -> {
                        System.out.printf("%s starts to think\n", name);
                        return startThinking(ctx, Duration.ofSeconds(5));
                    })
                    .build();
        }

        //When a hakker is thinking it can become hungry
        //and try to pick up its chopsticks and eat
        Behavior<HakkerMessage> thinking() {
            return Behaviors.setup(ctx -> {
                ActorRef<ChopstickAnswer> adapter = ctx.messageAdapter(ChopstickAnswer.class, ChopstickAnswerAdaptor::new);
                return Behaviors.receive(HakkerMessage.class)
                        .onMessageEquals(HakkerMessage.Eat.INSTANCE, msg -> {
                            left.tell(new ChopstickMessage.Take(adapter));
                            right.tell(new ChopstickMessage.Take(adapter));
                            return hungry();
                        })
                        .build();
            });
        }

        //When a hakker is hungry it tries to pick up its chopsticks and eat
        //When it picks one up, it goes into wait for the other
        //If the hakkers first attempt at grabbing a chopstick fails,
        //it starts to wait for the response of the other grab
        Behavior<HakkerMessage> hungry() {
            return Behaviors.receive(HakkerMessage.class)
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(left), (ctx, msg) ->
                            waitForOtherChopstick(right, left)
                    )
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(right), (ctx, msg) ->
                            waitForOtherChopstick(left, right)
                    )
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(), (ctx, msg) ->
                            firstChopstickDenied()
                    )
                    .build();
        }

        //When a hakker is waiting for the last chopstick it can either obtain it
        //and start eating, or the other chopstick was busy, and the hakker goes
        //back to think about how he should obtain his chopsticks :-)
        Behavior<HakkerMessage> waitForOtherChopstick(ActorRef<ChopstickMessage> chopstickToWaitFor,
                                                      ActorRef<ChopstickMessage> takenChopstick) {
            return Behaviors.setup(ctx -> {
                ActorRef<ChopstickAnswer> adapter = ctx.messageAdapter(ChopstickAnswer.class, ChopstickAnswerAdaptor::new);
                return Behaviors.receive(HakkerMessage.class)
                        .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(chopstickToWaitFor), (context, msg) -> {
                            System.out.printf("%s has picked up %s and %s and starts to eat\n",
                                    name, left.path().name(), right.path().name());
                            return startEating(ctx, Duration.ofSeconds(5));
                        })
                        .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(chopstickToWaitFor), (context, msg) -> {
                            takenChopstick.tell(new ChopstickMessage.Put(adapter));
                            return startThinking(ctx, Duration.ofMillis(10));
                        })
                        .build();
            });
        }

        //When a hakker is eating, he can decide to start to think,
        //then he puts down his chopsticks and starts to think
        Behavior<HakkerMessage> eating() {
            return Behaviors.setup(ctx -> {
                ActorRef<ChopstickAnswer> adapter = ctx.messageAdapter(ChopstickAnswer.class, ChopstickAnswerAdaptor::new);
                return Behaviors.receive(HakkerMessage.class)
                        .onMessageEquals(HakkerMessage.Think.INSTANCE, ignore -> {
                            System.out.printf("%s puts down his chopsticks and starts to think\n", name);
                            left.tell(new ChopstickMessage.Put(adapter));
                            right.tell(new ChopstickMessage.Put(adapter));
                            return startThinking(ctx, Duration.ofSeconds(5));
                        })
                        .build();
            });
        }

        //When the results of the other grab comes back,
        //he needs to put it back if he got the other one.
        //Then go back and think and try to grab the chopsticks again
        Behavior<HakkerMessage> firstChopstickDenied() {
            return Behaviors.setup(context -> {
                ActorRef<ChopstickAnswer> adapter = context.messageAdapter(ChopstickAnswer.class, ChopstickAnswerAdaptor::new);
                return Behaviors.receive(HakkerMessage.class)
                        .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy() ,(ctx, msg) -> {
                            msg.msg.getChopstick().tell(new ChopstickMessage.Put(adapter));
                            return startThinking(ctx, Duration.ofMillis(10));
                        })
                        .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(), (ctx, msg) ->
                            startThinking(ctx, Duration.ofMillis(10))
                        )
                        .build();
            });
        }

        private Behavior<HakkerMessage> startThinking(ActorContext<HakkerMessage> ctx, Duration duration) {
            ctx.scheduleOnce(duration, ctx.getSelf(), HakkerMessage.Eat.INSTANCE);
            return thinking();
        }

        private Behavior<HakkerMessage> startEating(ActorContext<HakkerMessage> ctx, Duration duration) {
            ctx.scheduleOnce(duration, ctx.getSelf(), HakkerMessage.Think.INSTANCE);
            return eating();
        }
    }

    static Behavior<NotUsed> mainBehavior() {
        return Behaviors.setup(context -> {

            //Create 5 chopsticks
            ActorRef<ChopstickMessage>[] chopsticks = new ActorRef[5];
            for (int i = 0; i < 5; i++) {
                chopsticks[i] = context.spawn(Chopstick.available, "Chopstick" + i);
            }

            //Create 5 awesome hakkers and assign them their left and right chopstick
            List<String> names = Arrays.asList("Ghosh", "Boner", "Klang", "Krasser", "Manie");
            List<ActorRef> hakkers = new ArrayList<>();
            int i = 0;
            for (String name : names) {
                Hakker hakker = new Hakker(name, chopsticks[i], chopsticks[(i + 1) % 5]);
                hakkers.add(context.spawn(hakker.waiting(), name));
                i++;
            }
            //Signal all hakkers that they should start thinking, and watch the show
            hakkers.stream().forEach(hakker -> hakker.tell(HakkerMessage.Think.INSTANCE));
            return Behaviors.empty();
        });
    }

    /*
     * Alright, here's our test-harness
     */
    public static void main(String[] args) {
        ActorSystem.create(mainBehavior(), "DinningHakkers");
    }
}
