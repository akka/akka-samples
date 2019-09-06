package sample.cluster.simple

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props

object SimpleClusterApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args // FIXME testing travis, should not compile
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      // To use artery instead of netty, change to "akka.remote.artery.canonical.port"
      // See https://doc.akka.io/docs/akka/current/remoting-artery.html for details
      val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        """).withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ClusterSystem", config)
      // Create an actor that handles cluster domain events
      system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
    }
  }

}

