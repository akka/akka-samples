package sample.cluster.client.grpc;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import com.typesafe.config.Config;

import java.time.Duration;

public class ClusterClientSettings {

  /**
   * Create settings from the default configuration
   * `sample.cluster.client.grpc`.
   */
  public static ClusterClientSettings create(ActorSystem system) {
    Config config = system.settings().config().getConfig("sample.cluster.client.grpc");
      // FIXME service discovery
    GrpcClientSettings grpcClientSettings = GrpcClientSettings.connectToServiceAt("127.0.0.1", 50051, system)
      .withDeadline(Duration.ofSeconds(3)) // FIXME config
      .withTls(false);

    return new ClusterClientSettings(config.getInt("buffer-size"), grpcClientSettings);
  }

  public final int bufferSize;
  public final GrpcClientSettings grpcClientSettings;

  public ClusterClientSettings(int bufferSize, GrpcClientSettings grpcClientSettings) {
    this.bufferSize = bufferSize;
    this.grpcClientSettings = grpcClientSettings;
  }
}
