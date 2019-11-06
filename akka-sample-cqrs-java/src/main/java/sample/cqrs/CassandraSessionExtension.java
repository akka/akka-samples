package sample.cqrs;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Extension;
import akka.actor.typed.ExtensionId;
import akka.actor.typed.javadsl.Adapter;
import akka.event.Logging;
import akka.persistence.cassandra.ConfigSessionProvider;
import akka.persistence.cassandra.session.CassandraSessionSettings;
import akka.persistence.cassandra.session.javadsl.CassandraSession;
import com.typesafe.config.Config;

import java.util.concurrent.CompletableFuture;

public class CassandraSessionExtension implements Extension {

  public static class Id extends ExtensionId<CassandraSessionExtension> {

    private static final Id instance = new Id();

    private Id() {}

    // called once per ActorSystem
    @Override
    public CassandraSessionExtension createExtension(ActorSystem<?> system) {
      return new CassandraSessionExtension(system);
    }

    public static CassandraSessionExtension get(ActorSystem<?> system) {
      return instance.apply(system);
    }
  }

  public final CassandraSession session;

  private CassandraSessionExtension(ActorSystem<?> system) {
    Config sessionConfig = system.settings().config().getConfig("cassandra-journal");
    this.session = new CassandraSession(
      Adapter.toClassic(system),
      new ConfigSessionProvider(Adapter.toClassic(system), sessionConfig),
      new CassandraSessionSettings(sessionConfig),
      system.executionContext(),
      Logging.getLogger(Adapter.toClassic(system), getClass()),
      "sample",
      s -> CompletableFuture.completedFuture(Done.getInstance()));
  }

}
