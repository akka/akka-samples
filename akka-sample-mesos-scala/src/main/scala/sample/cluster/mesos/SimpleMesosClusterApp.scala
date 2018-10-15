package sample.cluster.mesos

import akka.actor.{ActorSystem, Props}

object SimpleMesosClusterApp {
  def main(args: Array[String]): Unit = {

    val marathonConfig = MarathonConfig.discoverAkkaConfig()
    val clusterName: String = marathonConfig.getString("akka.cluster.name")

    // Create an Akka system
    val system = ActorSystem(clusterName, marathonConfig)

    // Create an actor that handles cluster domain events
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

  }

}
