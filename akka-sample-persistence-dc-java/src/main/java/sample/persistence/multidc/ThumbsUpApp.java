package sample.persistence.multidc;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.internal.adapter.ActorSystemAdapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.ReplicatedSharding;
import akka.cluster.sharding.typed.ReplicatedShardingExtension;
import akka.cluster.typed.Cluster;
import akka.management.javadsl.AkkaManagement;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import akka.persistence.typed.ReplicaId;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ThumbsUpApp {

  public static void main(String[] args) {
    if (args.length == 0) {
      startClusterInSameJvm();
    } else if (args[0].equals("cassandra")) {
      startCassandraDatabase();
      System.out.println("Started Cassandra, press Ctrl + C to kill");
      try {
        new CountDownLatch(1).await();
      } catch (InterruptedException e) {}
    } else {
      int port = Integer.valueOf(args[0]);
      String dc;
      if (args.length > 1)
        dc = args[1];
      else
        dc = "eu-west";

      startNode(port, dc);
    }
  }

  private static void startClusterInSameJvm() {
    startCassandraDatabase();

    startNode(2551, "eu-west");
    startNode(2552, "eu-central");
  }

  private static void startNode(int port, String dc) {

    ActorSystem<?> system = ActorSystem.create(Behaviors.empty(), "ClusterSystem", config(port, dc));
    Cluster cluster = Cluster.get(system);

    ReplicatedSharding<ThumbsUpCounter.Command> replicatedSharding = ReplicatedShardingExtension.get(system).init(ThumbsUpCounter.provider());

    if (port != 0) {
      ThumbsUpHttp.startServer(system, "0.0.0.0", 20000 + port, new ReplicaId(cluster.selfMember().dataCenter()), replicatedSharding);
      AkkaManagement.get(ActorSystemAdapter.toClassic(system)).start();
    }
  }

  private static Config config(int port, String dc) {
    return ConfigFactory.parseString(
      "akka.remote.artery.canonical.port = " + port + "\n" +
          "akka.management.http.port = 1" + port + "\n" +
          "akka.cluster.multi-data-center.self-data-center = " + dc + "\n")
      .withFallback(ConfigFactory.load("application.conf"));
  }

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  private static void startCassandraDatabase() {
    File databaseDirectory = new File("target/cassandra-db");
    CassandraLauncher.start(
      databaseDirectory,
      CassandraLauncher.DefaultTestConfigResource(),
      false,
      9042);
  }

}
