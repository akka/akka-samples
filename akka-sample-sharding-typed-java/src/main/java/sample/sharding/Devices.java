package sample.sharding;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletionStage;

public class Devices extends AbstractBehavior<Devices.Command> {

  public interface Command {}

  public enum UpdateDevice implements Command {
    INSTANCE
  }

  public enum ReadTemperatures implements Command {
    INSTANCE
  }

  private static class GetTemperatureReply implements Command {
    final Device.Temperature temp;

    private GetTemperatureReply(Device.Temperature temp) {
      this.temp = temp;
    }
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(context ->
        Behaviors.withTimers(timers -> {
          Device.init(context.getSystem());

          timers.startTimerWithFixedDelay(UpdateDevice.INSTANCE, UpdateDevice.INSTANCE, Duration.ofSeconds(1));
          timers.startTimerWithFixedDelay(ReadTemperatures.INSTANCE, ReadTemperatures.INSTANCE, Duration.ofSeconds(15));

        return new Devices(context);
      }));
  }

  private final ActorContext<Command> context;
  private final ClusterSharding sharding;
  private final ActorRef<Device.Temperature> temperatureAdapter;
  private final Random random = new Random();

  private final int numberOfDevices = 50;

  public Devices(ActorContext<Command> context) {
    this.context = context;

    this.sharding = ClusterSharding.get(context.getSystem());

    this.temperatureAdapter =
      context.messageAdapter(Device.Temperature.class, GetTemperatureReply::new);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
          .onMessage(UpdateDevice.class, m -> receiveUpdateDevice())
      .onMessage(ReadTemperatures.class, m -> receiveReadTemperatures())
      .onMessage(GetTemperatureReply.class, this::receiveTemperature)
      .build();
  }

  private Behavior<Command> receiveUpdateDevice() {
    int deviceId = random.nextInt(numberOfDevices);
    double temperature = 5 + 30 * random.nextDouble();
    Device.RecordTemperature msg = new Device.RecordTemperature(deviceId, temperature);
    context.getLog().info("Sending {}", msg);
    entityRefFor(deviceId).tell(msg);
    return this;
  }

  private Behavior<Command> receiveReadTemperatures() {
    for (int deviceId = 0; deviceId < numberOfDevices; deviceId++) {
      entityRefFor(deviceId).tell(new Device.GetTemperature(deviceId, temperatureAdapter));
    }
    return this;
  }

  private EntityRef<Device.Command> entityRefFor(int deviceId) {
    return sharding.entityRefFor(Device.TYPE_KEY, String.valueOf(deviceId));
  }

  private Behavior<Command> receiveTemperature(GetTemperatureReply reply) {
    Device.Temperature temp = reply.temp;
    if (temp.readings > 0)
      context.getLog().info(
        "Temperature of device {} is {} with average {} after {} readings",
        temp.deviceId,
        temp.latest,
        temp.average,
        temp.readings
      );
    return this;
  }

}
