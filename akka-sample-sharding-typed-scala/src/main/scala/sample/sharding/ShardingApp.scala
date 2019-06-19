package sample.sharding

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

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
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory
        .parseString("akka.remote.artery.canonical.port=" + port)
        .withFallback(ConfigFactory.load())

      // Create an Akka system, with Devices actor that starts the sharding and sends random messages
      ActorSystem(Devices(), "ShardingSystem", config)
    }
  }

}
