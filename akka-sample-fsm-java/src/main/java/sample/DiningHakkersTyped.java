package sample;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DiningHakkersTyped {

    private static Behavior<NotUsed> mainBehavior() {
        return Behaviors.setup(context -> {

            //Create 5 chopsticks
            List<ActorRef<Chopstick.ChopstickMessage>> chopsticks = IntStream.range(0, 5)
                    .mapToObj(i -> context.spawn(Chopstick.create(), "Chopstick" + i))
                    .collect(Collectors.toList());

            //Create 5 hakkers and assign them their left and right chopstick
            List<String> names = Arrays.asList("Ghosh", "Boner", "Klang", "Krasser", "Manie");
            IntStream.range(0, names.size())
                    .mapToObj(i -> {
                        Behavior<Hakker.HakkerMessage> hakker = Behaviors.setup(ctx -> new Hakker(ctx, names.get(i), chopsticks.get(i), chopsticks.get((i + 1) % 5)).waiting());
                        return context.spawn(hakker, names.get(i));
                    })
            .forEach(hakker -> hakker.tell(Hakker.Think.INSTANCE));
            //Signal all hakkers that they should start thinking, and watch the show
            return Behaviors.empty();
        });
    }

    public static void main(String[] args) {
        ActorSystem.create(mainBehavior(), "DinningHakkers");
    }
}
