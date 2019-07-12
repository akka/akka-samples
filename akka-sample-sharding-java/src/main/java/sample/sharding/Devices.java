package sample.sharding;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletionStage;

import akka.actor.AbstractActorWithTimers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.pattern.Patterns;

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
        return String.valueOf(((Device.RecordTemperature) message).deviceId);
      else if (message instanceof Device.GetTemperature)
        return String.valueOf(((Device.GetTemperature) message).deviceId);
      else
        return null;
    }
  };

  public enum UpdateDevice {
    INSTANCE
  }

  public enum ReadTemperatures {
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
    getTimers().startTimerWithFixedDelay(ReadTemperatures.INSTANCE, ReadTemperatures.INSTANCE, Duration.ofSeconds(15));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(UpdateDevice.class, m -> receiveUpdateDevice())
      .match(ReadTemperatures.class, m -> receiveReadTemperatures())
      .match(Device.Temperature.class, this::receiveTemperature)
      .build();
  }

  private void receiveUpdateDevice() {
    Integer deviceId = random.nextInt(numberOfDevices);
    Double temperature = 5 + 30 * random.nextDouble();
    Device.RecordTemperature msg = new Device.RecordTemperature(deviceId, temperature);
    log.info("Sending {}", msg);
    deviceRegion.tell(msg, getSelf());
  }

  private void receiveReadTemperatures() {
    for (int deviceId = 0; deviceId < numberOfDevices; deviceId++) {
      if (deviceId >= 40) {
        CompletionStage<Object> reply = Patterns.ask(deviceRegion, new Device.GetTemperature(deviceId), Duration.ofSeconds(3));
        Patterns.pipe(reply, getContext().getDispatcher()).to(getSelf());
      } else {
        deviceRegion.tell(new Device.GetTemperature(deviceId), getSelf());
      }
    }
  }

  private void receiveTemperature(Device.Temperature temp) {
    if (temp.readings > 0)
      log.info(
        "Temperature of device {} is {} with average {} after {} readings",
        temp.deviceId,
        temp.latest,
        temp.average,
        temp.readings
      );
  }

}
