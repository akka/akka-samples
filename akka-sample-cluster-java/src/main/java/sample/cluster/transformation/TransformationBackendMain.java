package sample.cluster.transformation;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;

public class TransformationBackendMain {

  public static void main(String[] args) {
    // Override the configuration of the port when specified as program argument
    // To use artery instead of netty, change to "akka.remote.artery.canonical.port"
    // See https://doc.akka.io/docs/akka/current/remoting-artery.html for details
    final String port = args.length > 0 ? args[0] : "0";
    final Config config = 
      ConfigFactory.parseString(
          "akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]"))
      .withFallback(ConfigFactory.load());

    ActorSystem system = ActorSystem.create("ClusterSystem", config);

    system.actorOf(Props.create(TransformationBackend.class), "backend");

  }

}
