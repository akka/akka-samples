package sample.cluster.client.grpc;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;

import java.time.Duration;
import java.util.Optional;

public class ClusterReceptionistSettings {

  /**
   * Create settings from the default configuration
   * `sample.cluster.client.grpc.receptionist`.
   */
  public static ClusterReceptionistSettings create(ActorSystem system) {
    Config config = system.settings().config().getConfig("sample.cluster.client.grpc.receptionist");

    Optional<String> roleOption;
    String role = config.getString("role");
    if (role.equals(""))
      roleOption = Optional.empty();
    else
      roleOption = Optional.of(role);

    return new ClusterReceptionistSettings(
      new HostPort(config.getString("canonical.hostname"), config.getInt("canonical.port")),
      roleOption,
      config.getInt("buffer-size"),
      config.getDuration("ask-send-timeout"));
  }

  public static class HostPort {
    public final String hostname;
    public final int port;

    public HostPort(String hostname, int port) {
      this.hostname = hostname;
      this.port = port;
    }

    @Override
    public String toString() {
      return hostname + ":" + port;
    }

  }

  public final HostPort hostPort;
  public final Optional<String> role;
  public final int bufferSize;
  public final Duration askSendTimeout;

  public ClusterReceptionistSettings(HostPort hostPort, Optional<String> role, int bufferSize, Duration askSendTimeout) {
    this.hostPort = hostPort;
    this.role = role;
    this.bufferSize = bufferSize;
    this.askSendTimeout = askSendTimeout;
  }
}
