/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package sample.persistence.res.counter;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.ReplicatedSharding;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.persistence.typed.ReplicaId;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.http.javadsl.server.PathMatchers.segment;
import static akka.http.javadsl.server.PathMatchers.segments;

public class ThumbsUpHttp extends AllDirectives {

  public static void startServer(ActorSystem<?> system, String httpHost, int httpPort, ReplicaId selfReplica, ReplicatedSharding<ThumbsUpCounter.Command> replicatedSharding) {

    ThumbsUpHttp api = new ThumbsUpHttp();
    final Route routeFlow =
        api.createRoute(selfReplica, replicatedSharding);
    final CompletionStage<ServerBinding> binding = Http.get(system).newServerAt(httpHost, httpPort)
            .bind(routeFlow);

    binding.thenAccept(b ->
        system.log().info("HTTP Server bound to http://{}:{}", httpHost, httpPort)
    );
  }

  private Route createRoute(ReplicaId selfReplica, ReplicatedSharding<ThumbsUpCounter.Command> replicatedSharding) {
    Duration timeout = Duration.ofSeconds(10);
    return
        pathPrefix("thumbs-up", () ->
            concat(
                // example: curl http://0.0.0.0:22551/thumbs-up/a
                get(() -> path(segment(), resourceId -> {
                  return onComplete(replicatedSharding.getEntityRefsFor(resourceId).get(selfReplica).<ThumbsUpCounter.State>ask(replyTo ->  new ThumbsUpCounter.GetUsers(resourceId, replyTo), timeout), state -> {
                    Source<ByteString, NotUsed> s =
                        Source.fromIterator(() -> (state.get()).users.iterator())
                            .intersperse("\n")
                            .map(ByteString::fromString);
                    return complete(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, s));
                  });
                })),
                // example: curl -X POST http://0.0.0.0:22551/thumbs-up/a/u1
                post(() -> {
                  return path(segments(2), seg -> {
                    final String resourceId = seg.get(0);
                    final String userId = seg.get(1);
                    return onComplete(replicatedSharding.getEntityRefsFor(resourceId).get(selfReplica).<Long>ask(replyTo -> new ThumbsUpCounter.GiveThumbsUp(resourceId, userId, replyTo), timeout), cnt -> {
                      return complete(cnt.get().toString());
                    });
                  });
                })
            )
        );

  }

}
