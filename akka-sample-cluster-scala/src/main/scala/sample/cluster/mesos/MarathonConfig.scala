package sample.cluster.mesos

import java.io.File

import com.typesafe.config._

/**
  * This object discovers seed nodes for the Akka Cluster using Marathon API
  */
object MarathonConfig {

  val config: Config = ConfigFactory.load()

  /**
    * Env var that contains the Host's IP Address
    */
  val HOST_IP_ENV_VAR = "LIBPROCESS_IP"

  /**
    * Env var that contains the Host's PORT bound to the container port
    */
  val HOST_PORT_ENV_VAR = "PORT_"

  /**
    * Docker's internal port used for the cluster.
    */
  val bindPort: String = config.getString("akka.remote.netty.tcp.bind-port")
  /**
    * Docker's internal hostname. It should be configured from $HOSTNAME env var
    */
  val bindHost: String = config.getString("akka.remote.netty.tcp.bind-hostname")

  lazy val hostIP: String = discoverHostIP()
  lazy val hostPort: String = discoverHostPort()

  /**
    * Use Marathon API to discover other existing nodes for this app.
    *
    * @return an array of strings with IP:PORT
    */
  def getSeedNodes(): Seq[String] = {
    val url: String = config.getString("akka.cluster.discovery.url")
    val portIndex: Int = config.getInt("akka.cluster.discovery.port-index")
    val clusterName: String = config.getString("akka.cluster.name")

    val tmpCfg = ConfigFactory
      //.parseURL(new URL(url))
      .parseFileAnySyntax(new File("/Users/ddascal/tmp/marathon-tasks.json"))
      .resolve()

    var seq: Seq[String] = Seq()
    tmpCfg.getConfigList("tasks").forEach(
      (item: Config) =>
        seq = seq :+ ("akka.tcp://%s@%s:%s" format(clusterName, item.getString("host"), item.getIntList("ports").get(portIndex).toString)))

    seq
  }

  def discoverAkkaConfig(): Config = {

    val seedNodes = getSeedNodes().map { address =>
      s"""akka.cluster.seed-nodes += "$address""""
    }.mkString("\n")


    //seedNodes.mkString("\n")

    ConfigFactory.parseString(seedNodes)
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(hostIP))
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(hostPort))
      .withFallback(config)
      .resolve()
  }

  private def discoverHostIP(): String = {
    sys.env(HOST_IP_ENV_VAR)
  }

  private def discoverHostPort(): String = {
    sys.env(HOST_PORT_ENV_VAR + bindPort)
  }

}