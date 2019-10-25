package sample.sharding

import akka.actor.typed.ActorSystem
import akka.cluster.typed.{ Cluster, Join }
import com.typesafe.config.ConfigFactory

/**
 * See the README.md for starting each node with sbt.
 */
object ShardingApp {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }

  def startup(ports: Seq[String]): Unit = {
    // In a production application you wouldn't typically start multiple ActorSystem instances in the
    // same JVM, here we do it to easily demonstrate these ActorSytems (which would be in separate JVM's)
    // talking to each other.

    ports.foreach { port =>
      // Override the configuration of the port
      val config =
        ConfigFactory.parseString("akka.remote.artery.canonical.port=" + port).withFallback(ConfigFactory.load())

      // Create an Akka system and an actor that starts the sharding
      // and sends random messages
      val system = ActorSystem[Message](TemperatureService(), "ShardingSystem", config)

      val cluster = Cluster(system)
      cluster.manager ! Join(cluster.selfMember.address)

    }
  }

}
