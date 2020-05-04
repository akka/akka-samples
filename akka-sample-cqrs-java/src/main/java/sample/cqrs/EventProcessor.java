package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ClusterShardingSettings;
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.stream.KillSwitches;
import akka.stream.SharedKillSwitch;

import java.util.Optional;
import java.util.function.Function;

/**
 * General purpose event processor infrastructure. Not specific to the ShoppingCart domain.
 */
public class EventProcessor {

  public static <Event> void init(
    ActorSystem<?> system,
    EventProcessorSettings settings,
    Function<String, EventProcessorStream<Event>> eventProcessorStream) {

    ShardedDaemonProcessSettings shardedDaemonSettings =
      ShardedDaemonProcessSettings.create(system)
        .withKeepAliveInterval(settings.keepAliveInterval)
        .withShardingSettings(ClusterShardingSettings.create(system).withRole("read-model"));

    ShardedDaemonProcess.get(system)
      .init(Void.class, "event-processors-" + settings.id, settings.parallelism,
        i -> EventProcessor.create(eventProcessorStream.apply(settings.tagPrefix + "-" + i)),
        shardedDaemonSettings, Optional.empty());
  }

  public static Behavior<Void> create(EventProcessorStream<?> eventProcessorStream) {
    return Behaviors.setup(context -> {
      SharedKillSwitch killSwitch = KillSwitches.shared("eventProcessorSwitch");
      eventProcessorStream.runQueryStream(killSwitch);
      return Behaviors.receive(Void.class)
        .onSignal(PostStop.class, sig -> {
          killSwitch.shutdown();
          return Behaviors.same();
        })
        .build();
    });
  }


}
