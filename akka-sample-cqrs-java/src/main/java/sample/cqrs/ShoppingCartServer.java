package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.stream.Materializer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

class ShoppingCartServer {

  static void startHttpServer(Route route, int httpPort, ActorSystem<?> system) {
    // Akka HTTP still needs a classic ActorSystem to start
    akka.actor.ActorSystem classicSystem = Adapter.toClassic(system);
    final Http http = Http.get(classicSystem);
    final Materializer materializer = Materializer.matFromSystem(system);

    CompletionStage<ServerBinding> futureBinding =
      http.newServerAt("localhost", httpPort).bind(route);

    futureBinding
      .thenApply(binding -> binding.addToCoordinatedShutdown(Duration.ofSeconds(3), system))
      .whenComplete((binding, exception) -> {
        if (binding != null) {
          InetSocketAddress address = binding.localAddress();
          system.log().info("Server online at http://{}:{}/",
            address.getHostString(),
            address.getPort());
        } else {
          system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
          system.terminate();
        }
      });
  }

}
