package sample.sharding;

import java.time.Duration;
import java.util.Random;

import akka.actor.AbstractActorWithTimers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;

public class Devices extends AbstractActorWithTimers {

  public static Props props() {
    return Props.create(Devices.class, Devices::new);
  }

  private static final int NUMBER_OF_SHARDS = 100;

  static ShardRegion.MessageExtractor messageExtractor =
    new ShardRegion.HashCodeMessageExtractor(NUMBER_OF_SHARDS) {

    @Override
    public String entityId(Object message) {
      if (message instanceof Device.RecordTemperature)
        return ((Device.RecordTemperature) message).deviceId.toString();
      else
        return null;
    }
  };

  public enum UpdateDevice {
    INSTANCE
  }

  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private final ActorRef deviceRegion;

  private final Random random = new Random();

  private final int numberOfDevices = 50;

  public Devices() {
    ActorSystem system = getContext().getSystem();
    ClusterShardingSettings settings = ClusterShardingSettings.create(system);
    deviceRegion = ClusterSharding.get(system)
      .start(
        "Device",
        Device.props(),
        settings,
        messageExtractor);

    getTimers().startTimerWithFixedDelay(UpdateDevice.INSTANCE, UpdateDevice.INSTANCE, Duration.ofSeconds(1));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(UpdateDevice.class, u -> {
        Integer deviceId = random.nextInt(numberOfDevices);
        Double temperature = 5 + 30 * random.nextDouble();
        Device.RecordTemperature msg = new Device.RecordTemperature(deviceId, temperature);
        log.info("Sending {}", msg);
        deviceRegion.tell(msg, getSelf());
      })
      .build();
  }
}
