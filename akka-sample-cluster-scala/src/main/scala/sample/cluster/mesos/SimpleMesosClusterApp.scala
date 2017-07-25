package sample.cluster.mesos

import akka.actor.{ActorSystem, Props}
import sample.cluster.mesos.MarathonConfig.config
import sample.cluster.simple.SimpleClusterListener2

object SimpleMesosClusterApp {
  def main(args: Array[String]): Unit = {

//    val config = ConfigFactory.load()

    var marathonConfig = MarathonConfig.discoverAkkaConfig()

    val clusterName: String = marathonConfig.getString("akka.cluster.name")

    // Create an Akka system
    val system = ActorSystem(clusterName, marathonConfig)

    // Create an actor that handles cluster domain events
    system.actorOf(Props[SimpleClusterListener2], name = "clusterListener")

  }

}
