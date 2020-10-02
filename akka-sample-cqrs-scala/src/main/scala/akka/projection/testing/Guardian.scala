package akka.projection.testing

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardedDaemonProcessSettings, ShardingEnvelope}
import akka.cluster.typed.Cluster
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.ExactlyOnceProjection
import akka.projection.testing.LoadGeneration.{Result, RunTest}
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.util.Timeout
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object Guardian {

  def createProjectionFor(
                           settings: EventProcessorSettings,
                           index: Int,
                           factory: HikariFactory
                         )(implicit system: ActorSystem[_]): ExactlyOnceProjection[Offset, EventEnvelope[ConfigurablePersistentActor.Event]] = {
    val tag = s"tag-$index"
    val sourceProvider = EventSourcedProvider.eventsByTag[ConfigurablePersistentActor.Event](
      system = system,
      readJournalPluginId = CassandraReadJournal.Identifier,
      tag = tag)
    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("test-projection-id", tag),
      sourceProvider,
      () => factory.newSession(),
      () => new ProjectionHandler(tag, system)
    )
  }

  def apply(): Behavior[String] = {
    Behaviors.setup[String] { context =>
      implicit val system: ActorSystem[_] = context.system
      val config = new HikariConfig
      config.setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/")
      config.setUsername("docker")
      config.setPassword("docker")
      config.setMaximumPoolSize(19)
      config.setAutoCommit(false)
      val dataSource = new HikariDataSource(config)
      val settings = EventProcessorSettings(system)
      val shardRegion: ActorRef[ShardingEnvelope[ConfigurablePersistentActor.Command]] = ConfigurablePersistentActor.init(settings, system)
      if (Cluster(system).selfMember.hasRole("read-model")) {

        val dbSessionFactory = new HikariFactory(dataSource)

        // we only want to run the daemon processes on the read-model nodes
        val shardingSettings = ClusterShardingSettings(system)
        val shardedDaemonProcessSettings =
          ShardedDaemonProcessSettings(system).withShardingSettings(shardingSettings.withRole("read-model"))

        ShardedDaemonProcess(system).init(
          name = "test-projection",
          settings.parallelism,
          n => ProjectionBehavior(createProjectionFor(settings, n, dbSessionFactory)),
          shardedDaemonProcessSettings,
          Some(ProjectionBehavior.Stop))
      }

      // TODO move to route
      implicit val timeout: Timeout = 10.seconds
      val loadGeneration: ActorRef[LoadGeneration.RunTest] = context.spawn(LoadGeneration(shardRegion, dataSource), "load-generation")
      context.ask[RunTest, Result](loadGeneration, replyTo => LoadGeneration.RunTest(s"test-${System.currentTimeMillis()}", 2, 5, replyTo)) {
        case Success(value) =>
          context.log.info("Test passed {}", value)
          "success"
        case Failure(t) =>
          context.log.error("Test failed",t )
          "failure"
      }


      Behaviors.empty
    }
  }
}
