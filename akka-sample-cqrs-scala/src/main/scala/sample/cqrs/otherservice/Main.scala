package sample.cqrs.otherservice

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.grpc.GrpcClientSettings
import akka.projection.ProjectionBehavior
import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.AtLeastOnceCassandraProjection
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import sample.cqrs.grpc.ShoppingCartEventEnvelope
import sample.cqrs.grpc.ShoppingCartServiceClient

object Main {

  def main(args: Array[String]): Unit = {
    args.headOption match {

      case Some(portString) if portString.matches("""\d+""") =>
        val port = portString.toInt
        startNode(port)

      case None =>
        throw new IllegalArgumentException("port number required argument")
    }
  }

  def startNode(port: Int): Unit = {
    val system =
      ActorSystem[Nothing](Guardian(), "OtherService", config(port))

    createTables(system)
  }

  def config(port: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      """).withFallback(ConfigFactory.load("otherservice.conf"))

  def createTables(system: ActorSystem[_]): Unit = {
    val session =
      CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    // TODO use real replication strategy in real application
    val keyspaceStmt =
      """
      CREATE KEYSPACE IF NOT EXISTS akka_cqrs_other
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
      """

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS akka_cqrs_other.offset_store (
        projection_name text,
        partition int,
        projection_key text,
        offset text,
        manifest text,
        last_updated timestamp,
        PRIMARY KEY ((projection_name, partition), projection_key)
      )
      """

    // ok to block here, main thread
    Await.ready(session.executeDDL(keyspaceStmt), 30.seconds)
    system.log.info("Created akka_cqrs_other keyspace")
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
    system.log.info("Created akka_cqrs_other.offset_store table")

  }

}

object Guardian {

  def createProjectionFor(
      client: ShoppingCartServiceClient,
      system: ActorSystem[_],
      slice: Int): AtLeastOnceCassandraProjection[ShoppingCartEventEnvelope] = {
    val sourceProvider = new ShoppingCartGrpcEventSourceProvider(client, system, slice)
    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("shopping-events", slice.toString),
      sourceProvider,
      handler = new ShoppingCartEventsProjectionHandler(slice))
  }

  def apply(): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      val system = context.system
      import akka.actor.typed.scaladsl.adapter._
      implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
      implicit val ec: ExecutionContext = system.executionContext

      // this would use service discovery
      val clientSettings =
        GrpcClientSettings
          .fromConfig("ShoppingCartService")
          .withChannelBuilderOverrides(b => b.keepAliveWithoutCalls(true))
      val client = ShoppingCartServiceClient(clientSettings)

      ShardedDaemonProcess(system).init(
        "ShoppingCartGrpcEventProjection",
        4,
        n => ProjectionBehavior(createProjectionFor(client, system, n)),
        ShardedDaemonProcessSettings(system),
        Some(ProjectionBehavior.Stop))

      Behaviors.empty
    }
  }
}
