package sample.persistence.multidc;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.management.javadsl.AkkaManagement;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import akka.persistence.multidc.PersistenceMultiDcSettings;
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
    ActorSystem system = ActorSystem.create("ClusterSystem", config(port, dc));

    PersistenceMultiDcSettings persistenceMultiDcSettings = PersistenceMultiDcSettings.create(system);

    ActorRef counterRegion = ClusterSharding.get(system).start(
      ThumbsUpCounter.ShardingTypeName,
      ThumbsUpCounter.shardingProps(persistenceMultiDcSettings),
      ClusterShardingSettings.create(system),
      ThumbsUpCounter.messageExtractor);

    // The speculative replication requires sharding proxies to other DCs
    if (persistenceMultiDcSettings.useSpeculativeReplication()) {
      for (String otherDc: persistenceMultiDcSettings.getOtherDcs(Cluster.get(system).selfDataCenter())) {
        ClusterSharding.get(system).startProxy(
            ThumbsUpCounter.ShardingTypeName,
            Optional.empty(),
            Optional.of(otherDc),
            ThumbsUpCounter.messageExtractor);
      }
    }

    if (port != 0) {
      ThumbsUpHttp.startServer(system, "0.0.0.0", 20000 + port, counterRegion);

      AkkaManagement.get(system).start();
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
