package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.ClusterSingletonSettings;
import akka.cluster.typed.SingletonActor;

public class KeepAlive extends AbstractBehavior<KeepAlive.Probe> {

  enum Probe {
    INSTANCE
  }

  public static void init(ActorSystem<?> system, EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {
    EventProcessorSettings settings = EventProcessorSettings.create(system);
    ClusterSingleton.get(system).init(
      SingletonActor.of(KeepAlive.create(settings, eventProcessorEntityKey), "keepAlive-" + settings.id)
        .withSettings(ClusterSingletonSettings.create(system).withRole("read-model")));
  }

  public static Behavior<Probe> create(
    EventProcessorSettings settings,
    EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {

    return Behaviors.setup(context ->
      Behaviors.withTimers(timers -> {
        timers.startTimerWithFixedDelay(Probe.INSTANCE, Probe.INSTANCE, settings.keepAliveInterval);
        return new KeepAlive(context, settings, eventProcessorEntityKey);
      }));
  }

  private final EventProcessorSettings settings;
  private final EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey;
  private final ClusterSharding sharding;

  private KeepAlive(ActorContext<Probe> context, EventProcessorSettings settings, EntityTypeKey<EventProcessor.Ping> eventProcessorEntityKey) {
    super(context);
    this.settings = settings;
    this.eventProcessorEntityKey = eventProcessorEntityKey;
    this.sharding = ClusterSharding.get(context.getSystem());
  }

  @Override
  public Receive<Probe> createReceive() {
    return newReceiveBuilder()
      .onMessage(Probe.class, p -> onProbe())
      .build();
  }

  private Behavior<Probe> onProbe() {
    for (int i = 0; i < settings.parallelism; i++) {
      String eventProcessorId = settings.tagPrefix + "-" + i;
      sharding.entityRefFor(eventProcessorEntityKey, eventProcessorId).tell(EventProcessor.Ping.INSTANCE);
    }
    return this;
  }


}
