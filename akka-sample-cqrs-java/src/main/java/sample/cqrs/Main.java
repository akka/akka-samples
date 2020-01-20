package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import akka.persistence.cassandra.session.javadsl.CassandraSession;
import akka.persistence.cassandra.testkit.CassandraLauncher;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("port number, or cassandra required argument");
    } else if (args[0].equals("cassandra")) {
      startCassandraDatabase();
      System.out.println("Started Cassandra, press Ctrl + C to kill");
      new CountDownLatch(1).await();
    } else {
      String portString = args[0];
      int port = Integer.parseInt(portString);
      int httpPort = Integer.parseInt("80" + portString.substring(portString.length() - 2));
      startNode(port, httpPort);
    }

  }

  private static void startNode(int port, int httpPort) {
    ActorSystem<Void> system = ActorSystem.create(Guardian.create(), "Shopping", config(port, httpPort));

    if (Cluster.get(system).selfMember().hasRole("read-model"))
      createTables(system);
  }

  private static Config config(int port, int httpPort) {
    return ConfigFactory.parseString(
      "akka.remote.artery.canonical.port = " + port + "\n" +
      "shopping.http.port ="  + httpPort + "\n")
      .withFallback(ConfigFactory.load());
  }

  /**
   * To make the sample easier to run we kickstart a Cassandra instance to
   * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
   * in a real application a pre-existing Cassandra cluster should be used.
   */
  private static void startCassandraDatabase() {
    File databaseDirectory = new File("target/cassandra-db");
    CassandraLauncher.start(databaseDirectory, CassandraLauncher.DefaultTestConfigResource(), false, 9042);
  }

  static void createTables(ActorSystem<?> system) {
    CassandraSession session = CassandraSessionExtension.Id.get(system).session;

    // TODO use real replication strategy in real application
    String keyspaceStmt =
      "CREATE KEYSPACE IF NOT EXISTS akka_cqrs_sample \n" +
      "WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 } \n";

    String offsetTableStmt =
      "CREATE TABLE IF NOT EXISTS akka_cqrs_sample.offsetStore ( \n" +
      "  eventProcessorId text, \n" +
      "  tag text, \n" +
      "  timeUuidOffset timeuuid, \n" +
      "  PRIMARY KEY (eventProcessorId, tag) \n" +
      ") \n";

    // ok to block here, main thread
    try {
      session.executeDDL(keyspaceStmt).toCompletableFuture().get(30, TimeUnit.SECONDS);
      session.executeDDL(offsetTableStmt).toCompletableFuture().get(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}

class Guardian {
  static Behavior<Void> create() {
    return Behaviors.setup(context -> {
      ActorSystem<?> system = context.getSystem();
      EventProcessorSettings settings = EventProcessorSettings.create(system);
      int httpPort = system.settings().config().getInt("shopping.http.port");

      ShoppingCart.init(system, settings);

      if (Cluster.get(system).selfMember().hasRole("read-model")) {
        EventProcessor.init(
          system,
          settings,
          tag -> new ShoppingCartEventProcessorStream(system, settings.id, tag));
      }

      ShoppingCartServer.startHttpServer(new ShoppingCartRoutes(system).shopping(), httpPort, system);

      return Behaviors.empty();
    });
  }
}
