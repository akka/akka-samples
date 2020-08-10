package sample.cqrs;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

class ShoppingCartServer {

  static void startHttpServer(Route route, int httpPort, ActorSystem<?> system) {
    final Http http = Http.get(system);

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
