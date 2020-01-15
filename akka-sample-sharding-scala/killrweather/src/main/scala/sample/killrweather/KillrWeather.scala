package sample.killrweather

import akka.actor.AddressFromURIString
import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

/**
 * Main entry point for the application.
 * See the README.md for starting each node with sbt.
 */
object KillrWeather {

  def main(args: Array[String]): Unit = {
    val seedNodePorts = ConfigFactory.load().getStringList("akka.cluster.seed-nodes")
      .asScala
      .flatMap { case AddressFromURIString(s) => s.port }

    val ports = args.headOption match {
      case Some(port) => Seq(port.toInt)
      case None       =>
        // In a production application you wouldn't typically start multiple ActorSystem instances in the
        // same JVM, here we do it to easily demonstrate these ActorSystems (which would be in separate JVM's)
        // talking to each other.
        seedNodePorts ++ Seq(0)
    }

    ports.foreach { port =>
      val httpPort =
        if (port > 0) 10000 + port // offset from akka port
        else 0 // let OS decide

      val config = configWithPort(port)
      ActorSystem[Nothing](Guardian(httpPort), "KillrWeather", config)
    }
  }

  private def configWithPort(port: Int): Config =
    ConfigFactory.parseString(s"""
       akka.remote.artery.canonical.port = $port
        """).withFallback(ConfigFactory.load())

}
