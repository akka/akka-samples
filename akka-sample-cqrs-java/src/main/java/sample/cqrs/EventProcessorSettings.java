package sample.cqrs;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;

import java.time.Duration;

public class EventProcessorSettings {

  public static EventProcessorSettings create(ActorSystem<?> system) {
    return create(system.settings().config().getConfig("event-processor"));
  }

  public static EventProcessorSettings create(Config config) {
    return new EventProcessorSettings(
      config.getString("id"),
      config.getDuration("keep-alive-interval"),
      config.getString("tag-prefix"),
      config.getInt("parallelism")
    );
  }

  public final String id;
  public final Duration keepAliveInterval;
  public final String tagPrefix;
  public final int parallelism;

  public EventProcessorSettings(String id, Duration keepAliveInterval, String tagPrefix, int parallelism) {
    this.id = id;
    this.keepAliveInterval = keepAliveInterval;
    this.tagPrefix = tagPrefix;
    this.parallelism = parallelism;
  }
}
