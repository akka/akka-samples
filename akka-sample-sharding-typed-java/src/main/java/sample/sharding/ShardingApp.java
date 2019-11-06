package sample.sharding;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ShardingApp {

  public static void main(String[] args) {
    if (args.length == 0)
      startup(new String[] { "2551", "2552", "0" });
    else
      startup(args);
  }

  public static void startup(String[] ports) {
    for (String port : ports) {
      // Override the configuration of the port
      Config config = ConfigFactory.parseString(
          "akka.remote.artery.canonical.port=" + port).withFallback(
          ConfigFactory.load());

      // Create an Akka system
      ActorSystem.create(Devices.create(), "ShardingSystem", config);
    }
  }
}
