package sample.killrweather

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding

/**
 * Root actor bootstrapping the application
 */
object Guardian {

  def apply(httpPort: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
    WeatherStation.initSharding(context.system)
    val stations = context.spawn(Stations(), "stations")
    context.watch(stations)

    val routes = new WeatherRoutes(stations, ClusterSharding(context.system))(context.system)
    WeatherHttpServer.start(routes.weather, httpPort, context.system)

    Behaviors.empty
  }

}
