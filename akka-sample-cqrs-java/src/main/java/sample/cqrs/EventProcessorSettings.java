package sample.cqrs;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;

public class EventProcessorSettings {

  public static EventProcessorSettings create(ActorSystem<?> system) {
    return create(system.settings().config().getConfig("event-processor"));
  }

  public static EventProcessorSettings create(Config config) {
    return new EventProcessorSettings(
      config.getString("tag-prefix"),
      config.getInt("parallelism")
    );
  }

  public final String tagPrefix;
  public final int parallelism;

  public EventProcessorSettings(String tagPrefix, int parallelism) {
    this.tagPrefix = tagPrefix;
    this.parallelism = parallelism;
  }
}
