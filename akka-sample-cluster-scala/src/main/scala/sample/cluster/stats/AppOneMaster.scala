package sample.cluster.stats

import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.Routers
import akka.cluster.typed.Cluster
import akka.cluster.typed.ClusterSingleton
import akka.cluster.typed.ClusterSingletonSettings
import akka.cluster.typed.SingletonActor

object AppOneMaster {

  val WorkerServiceKey = ServiceKey[StatsWorker.Process]("Worker")

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup("compute", 25251)
      startup("compute", 25252)
      startup("compute", 0)
      startup("client", 0)
    } else {
      require(args.size == 2, "Usage: role port")
      startup(args(0), args(1).toInt)
    }
  }

  def startup(role: String, port: Int): Unit = {
    // Override the configuration of the port when specified as program argument
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [compute]
      """)
      .withFallback(ConfigFactory.load("stats"))

    val rootBehavior = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      val singletonSettings = ClusterSingletonSettings(ctx.system)
        .withRole("compute")
      val serviceSingleton = SingletonActor(
        Behaviors.setup[StatsService.Command] { ctx =>
          // the service singleton accesses available workers through a group router
          val workersRouter = ctx.spawn(Routers.group(WorkerServiceKey), "WorkersRouter")
          StatsService(workersRouter)
        },
        "StatsService"
      ).withStopMessage(StatsService.Stop)
        .withSettings(singletonSettings)
      val serviceProxy = ClusterSingleton(ctx.system).init(serviceSingleton)


      role match {
        case "compute" =>
          // on every compute node N local workers, which a cluster singleton stats service delegates work to
          val numberOfWorkers = ctx.system.settings.config.getInt("stats-service.workers-per-node")
          ctx.log.info("Starting {} workers", numberOfWorkers)
          (0 to numberOfWorkers).foreach { n =>
            val worker = ctx.spawn(StatsWorker(), s"StatsWorker$n")
            ctx.system.receptionist ! Receptionist.Register(WorkerServiceKey, worker)
          }
        case "client" =>
          ctx.spawn(StatsSampleClient(serviceProxy), "Client")
        case unknown =>
          throw new IllegalArgumentException(s"Unknown role $unknown")
      }


      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "ClusterSystem", config)
  }

}


