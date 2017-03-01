package sample.javaslang;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import javaslang.collection.List;
import javaslang.control.Option;
import javaslang.control.Try;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static javaslang.API.*;
import static javaslang.Patterns.*;

public class MazeRunner {

  public static final void main(String[] args) throws ExecutionException, InterruptedException {

    final List<String> maze = List.of(
      "┏━━━━━━┓",
      "┃      ┃",
      "┃     ⚑┃",
      "┗━━━━━━┛"
    );

    ActorSystem sys = ActorSystem.create("MazeRunner");
    ActorRef master = sys.actorOf(Master.props(new Maze(maze)));

    repl(master);

    sys.terminate();
  }

  private static void repl(ActorRef master) throws ExecutionException, InterruptedException {
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    CompletableFuture<Option<String>> output = CompletableFuture.completedFuture(Option.of(
      "Welcome to the Maze Runner.\n" +
      "Available commands are:\n" +
      "  spawn N - spawn N number of minions\n" +
      "  results - show minion results\n" +
      "  q - quit\n"
    ));

    do {
      System.out.println(output.get().getOrElse(""));
      System.out.print("> ");
      output = Match(Try.of(br::readLine)).of(
        Case(Success($("q")), CompletableFuture.completedFuture(Option.none())),
        Case(Success($(s -> s.startsWith("spawn "))), s ->
          Match(tryParseInt(s)).of(
            Case(Some($()), i -> {
              master.tell(new Master.Start(i), ActorRef.noSender());
              return CompletableFuture.completedFuture(Option.of("Spawning " + i + " minions."));
            }),
            Case(None(), CompletableFuture.completedFuture(Option.of("Unable to parse minion count to spawn.")))
          )
        ),
        Case(Success($("results")), r ->
          PatternsCS
            .ask(master, new Master.GetResults(), Timeout.apply(1, TimeUnit.SECONDS))
            .thenApply(results -> Option.of(results.toString()))
            .toCompletableFuture()
        ),
        Case(Success($()), command -> CompletableFuture.completedFuture(Option.of("Unknown command: " + command))),
        Case(Failure($()), ex -> CompletableFuture.completedFuture(Option.of("Was not able to get next command. (" + ex.getMessage() + ")")))
      );
    } while (output.get().isDefined());
  }

  private static Option<Integer> tryParseInt(String s) {
    return Option.of(s.replace("spawn ", "")).flatMap(count -> Try.of(() -> Integer.valueOf(count)).toOption());
  }

}