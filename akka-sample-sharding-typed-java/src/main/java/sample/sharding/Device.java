package sample.sharding;

import java.util.ArrayList;
import java.util.List;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import com.fasterxml.jackson.annotation.JsonCreator;

public class Device extends AbstractBehavior<Device.Command> {

  public static final EntityTypeKey<Command> TYPE_KEY = EntityTypeKey.create(Command.class, "Device");

  public static void init(ActorSystem<?> system) {
    // If the original hashing function was using
    // `(math.abs(id.hashCode) % numberOfShards).toString`
    // the default HashCodeMessageExtractor in Typed can be used.
    // That is also compatible with `akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor`.
    ClusterSharding.get(system).init(Entity.of(TYPE_KEY, context -> Device.create()));
  }

  public interface Command extends Message {}

  public static class RecordTemperature implements Command {
    public final int deviceId;
    public final double temperature;

    public RecordTemperature(int deviceId, double temperature) {
      this.deviceId = deviceId;
      this.temperature = temperature;
    }

    @Override
    public String toString() {
      return "RecordTemperature(" + deviceId + ", " + temperature + ")";
    }
  }

  public static class GetTemperature implements Command {
    public final int deviceId;
    public final ActorRef<Temperature> replyTo;

    @JsonCreator
    public GetTemperature(int deviceId, ActorRef<Temperature> replyTo) {
      this.deviceId = deviceId;
      this.replyTo = replyTo;
    }
  }

  public static class Temperature implements Message {
    public final int deviceId;
    public final double average;
    public final double latest;
    public final int readings;

    public Temperature(int deviceId, double average, double latest, int readings) {
      this.deviceId = deviceId;
      this.average = average;
      this.latest = latest;
      this.readings = readings;
    }

    @Override
    public String toString() {
      return "Temperature(" + deviceId + ", " + average + ", " + latest+ ", " + readings + ")";
    }
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(Device::new);
  }

  private final ActorContext<Command> context;
  private List<Double> temperatures = new ArrayList<Double>();

  private Device(ActorContext<Command> context) {
    this.context = context;
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
      .onMessage(RecordTemperature.class, this::receiveRecordTemperature)
      .onMessage(GetTemperature.class, this::receiveGetTemperature)
      .build();
  }

  private Behavior<Command> receiveRecordTemperature(RecordTemperature cmd) {
    temperatures.add(cmd.temperature);
    context.getLog().info("Recording temperature {} for device {}, average is {} after {} readings",
      cmd.temperature, cmd.deviceId, average(temperatures), temperatures.size());
    return this;
  }

  private Behavior<Command> receiveGetTemperature(GetTemperature cmd) {
    Temperature reply;
    if (temperatures.isEmpty()) {
      reply = new Temperature(cmd.deviceId, Double.NaN, Double.NaN, 0);
    } else {
      reply = new Temperature(cmd.deviceId, average(temperatures),
        temperatures.get(temperatures.size() - 1), temperatures.size());
    }

    cmd.replyTo.tell(reply);
    return this;
  }

  private double sum(List<Double> values) {
    double result = 0.0;
    for (double d : values) {
      result += d;
    }
    return result;
  }

  private double average(List<Double> values) {
    if (values.isEmpty())
      return Double.NaN;
    else
      return sum(values) / values.size();
  }
}
