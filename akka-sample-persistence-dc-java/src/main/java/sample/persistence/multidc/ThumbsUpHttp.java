package sample.persistence.multidc;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.Timeout;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.http.javadsl.server.PathMatchers.segment;
import static akka.http.javadsl.server.PathMatchers.segments;
import static akka.pattern.PatternsCS.ask;

public class ThumbsUpHttp extends AllDirectives {

  public static void startServer(ActorSystem system, String httpHost, int httpPort, ActorRef counterRegion) {

    final ActorMaterializer materializer = ActorMaterializer.create(system);

    ThumbsUpHttp api = new ThumbsUpHttp();

    final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow =
        api.createRoute(counterRegion).flow(system, materializer);
    final CompletionStage<ServerBinding> binding = Http.get(system).bindAndHandle(routeFlow,
        ConnectHttp.toHost(httpHost, httpPort), materializer);

    binding.thenAccept(b ->
        system.log().info("HTTP Server bound to http://{}:{}", httpHost, httpPort)
    );
  }

  private Route createRoute(ActorRef counterRegion) {
    Timeout timeout = Timeout.apply(10, TimeUnit.SECONDS);
    return
        pathPrefix("thumbs-up", () ->
            route(
                // example: curl http://0.0.0.0:22551/thumbs-up/a
                get(() -> {
                  return path(segment(), resourceId -> {
                    return onComplete(ask(counterRegion, new ThumbsUpCounter.GetUsers(resourceId), timeout), state -> {
                      Source<ByteString, NotUsed> s =
                          Source.fromIterator(() -> ((ThumbsUpCounter.State) state.get()).users.iterator())
                              .intersperse("\n")
                              .map(line -> ByteString.fromString(line));
                      return complete(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, s));
                    });
                  });
                }),
                // example: curl -X POST http://0.0.0.0:22551/thumbs-up/a/u1
                post(() -> {
                  return path(segments(2), seg -> {
                    final String resourceId = seg.get(0);
                    final String userId = seg.get(1);
                    return onComplete(ask(counterRegion, new ThumbsUpCounter.GiveThumbsUp(resourceId, userId), timeout), cnt -> {
                      return complete(cnt.get().toString());
                    });
                  });
                })
            )
        );

  }

}
