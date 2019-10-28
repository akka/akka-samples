package sample;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import sample.Chopstick.ChopstickAnswer;
import sample.Chopstick.ChopstickMessage;

import java.time.Duration;

/*
 * A hakker is an awesome dude or dudette who either thinks about hacking or has to eat ;-)
 */
class Hakker {

    interface HakkerMessage { }

    enum Eat implements HakkerMessage {
        INSTANCE
    }

    enum Think implements HakkerMessage {
        INSTANCE
    }

    static class ChopstickAnswerAdaptor implements HakkerMessage {
        final ChopstickAnswer msg;

        public ChopstickAnswerAdaptor(ChopstickAnswer msg) {
            this.msg = msg;
        }
    }
    private final ActorContext<HakkerMessage> ctx;
    private final String name;
    private final ActorRef<ChopstickMessage> left;
    private final ActorRef<ChopstickMessage> right;

    public Hakker(ActorContext<HakkerMessage> ctx, String name, ActorRef<ChopstickMessage> left, ActorRef<ChopstickMessage> right) {
        this.ctx = ctx;
        this.name = name;
        this.left = left;
        this.right = right;
    }

    Behavior<HakkerMessage> waiting() {
        return Behaviors.receive(HakkerMessage.class)
                .onMessage(Think.class, (msg) -> {
                    ctx.getLog().info("{} starts to think", name);
                    return startThinking(Duration.ofSeconds(5));
                })
                .build();
    }

    //When a hakker is thinking it can become hungry
    //and try to pick up its chopsticks and eat
    Behavior<HakkerMessage> thinking() {
        ActorRef<ChopstickAnswer> adapter = ctx.messageAdapter(ChopstickAnswer.class, ChopstickAnswerAdaptor::new);
        return Behaviors.receive(HakkerMessage.class)
                .onMessageEquals(Eat.INSTANCE, () -> {
                    left.tell(new Chopstick.Take(adapter));
                    right.tell(new Chopstick.Take(adapter));
                    return hungry();
                })
                .build();
    }

    //When a hakker is hungry it tries to pick up its chopsticks and eat
    //When it picks one up, it goes into wait for the other
    //If the hakkers first attempt at grabbing a chopstick fails,
    //it starts to wait for the response of the other grab
    Behavior<HakkerMessage> hungry() {
        return Behaviors.receive(HakkerMessage.class)
                .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(left), (msg) ->
                        waitForOtherChopstick(right, left)
                )
                .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(right), (msg) ->
                        waitForOtherChopstick(left, right)
                )
                .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(), (msg) ->
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
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(chopstickToWaitFor), msg -> {
                        ctx.getLog().info("{} has picked up {} and{} and starts to eat",
                                name, left.path().name(), right.path().name());
                        return startEating(ctx, Duration.ofSeconds(5));
                    })
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(chopstickToWaitFor), msg -> {
                        takenChopstick.tell(new Chopstick.Put(adapter));
                        return startThinking(Duration.ofMillis(10));
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
                    .onMessageEquals(Think.INSTANCE, () -> {
                        ctx.getLog().info("{} puts down his chopsticks and starts to think", name);
                        left.tell(new Chopstick.Put(adapter));
                        right.tell(new Chopstick.Put(adapter));
                        return startThinking(Duration.ofSeconds(5));
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
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isTakenBy(), msg -> {
                        msg.msg.getChopstick().tell(new Chopstick.Put(adapter));
                        return startThinking(Duration.ofMillis(10));
                    })
                    .onMessage(ChopstickAnswerAdaptor.class, m -> m.msg.isBusy(), (msg) ->
                            startThinking(Duration.ofMillis(10))
                    )
                    .build();
        });
    }

    private Behavior<HakkerMessage> startThinking(Duration duration) {
        ctx.scheduleOnce(duration, ctx.getSelf(), Eat.INSTANCE);
        return thinking();
    }

    private Behavior<HakkerMessage> startEating(ActorContext<HakkerMessage> ctx, Duration duration) {
        ctx.scheduleOnce(duration, ctx.getSelf(), Think.INSTANCE);
        return eating();
    }
}
