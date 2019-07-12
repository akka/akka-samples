package sample.sharding;

import java.util.ArrayList;
import java.util.List;

import akka.actor.*;
import akka.event.*;
import com.fasterxml.jackson.annotation.JsonCreator;

public class Device extends AbstractActor {

  public static Props props() {
    return Props.create(Device.class, Device::new);
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
    public final ActorRef replyTo;

    @JsonCreator
    public GetTemperature(int deviceId, ActorRef replyTo) {
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

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private List<Double> temperatures = new ArrayList<Double>();

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(RecordTemperature.class, this::receiveRecordTemperature)
      .match(GetTemperature.class, this::receiveGetTemperature)
      .build();
  }

  private void receiveRecordTemperature(RecordTemperature cmd) {
    temperatures.add(cmd.temperature);
    log.info("Recording temperature {} for device {}, average is {} after {} readings",
      cmd.temperature, cmd.deviceId, average(temperatures), temperatures.size());
  }

  private void receiveGetTemperature(GetTemperature cmd) {
    Temperature reply;
    if (temperatures.isEmpty()) {
      reply = new Temperature(cmd.deviceId, Double.NaN, Double.NaN, 0);
    } else {
      reply = new Temperature(cmd.deviceId, average(temperatures),
        temperatures.get(temperatures.size() - 1), temperatures.size());
    }

    if (cmd.replyTo == null)
      getSender().tell(reply, getSelf());
    else
      cmd.replyTo.tell(reply, getSelf());
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
