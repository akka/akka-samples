package sample.vavr;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import io.vavr.collection.List;
import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static io.vavr.API.*;
import static io.vavr.Patterns.*;

public class MazeRunner {

  final static List<String> maze = List.of(
    "+--+--+--+--+--+--+--+--+--+--+",
    "|           |        |     |  |",
    "+  +--+--+  +  +--+  +  +  +  +",
    "|     |     |  |     |  |  |  |",
    "+--+  +  +--+  +  +--+--+  +  +",
    "|  |  |     |  |              |",
    "+  +  +--+  +  +--+  +--+  +--+",
    "|  |  |        |     |     |  |",
    "+  +  +  +--+--+  +--+--+  +  +",
    "|     |  |        |  |     |  |",
    "+--+--+--+  +--+--+  +  +--+  +",
    "|                 |  |  |     |",
    "+  +--+--+--+--+  +  +  +  +--+",
    "|  |     |     |  |     |     |",
    "+  +  +  +--+  +  +--+--+--+  +",
    "|     |  |     |           |  |",
    "+--+--+  +  +--+--+--+  +--+  +",
    "|           |     |     |     |",
    "+  +--+  +--+--+  +  +--+  +--+",
    "|     |           |          ⚑|",
    "+--+--+--+--+--+--+--+--+--+--+"
  );

  public static final void main(String[] args) throws ExecutionException, InterruptedException {
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
      "  heatmap - show minion journey heatmap\n" +
      "  q - quit\n"
    ));

    do {
      System.out.println(output.get().getOrElse(""));
      System.out.print("> ");
      output = Match(Try.of(br::readLine)).of(
        Case($Success($("q")), CompletableFuture.completedFuture(Option.none())),
        Case($Success($(s -> s.startsWith("spawn "))), s ->
          Match(tryParseInt(s)).of(
            Case($Some($()), i -> {
              master.tell(new Master.Start(i), ActorRef.noSender());
              return CompletableFuture.completedFuture(Option.of("Spawning " + i + " minions."));
            }),
            Case($None(), CompletableFuture.completedFuture(Option.of("Unable to parse minion count to spawn.")))
          )
        ),
        Case($Success($("results")), r ->
          PatternsCS
            .ask(master, new Master.GetResults(), Timeout.apply(1, TimeUnit.SECONDS))
            .thenApply(results -> Option.of(results.toString()))
            .toCompletableFuture()
        ),
        Case($Success($("heatmap")), r ->
          PatternsCS
            .ask(master, new Master.GetResults(), Timeout.apply(1, TimeUnit.SECONDS))
            .thenApply(results -> (List<Minion.Stopped>)results)
            .thenApply(MazeRunner::getHeatMapValues)
            .thenApply(MazeRunner::getHeatMapTiles)
            .thenApply(MazeRunner::getHeatMap)
            .thenApply(heatMap -> heatMap.mkString("\n"))
            .thenApply(Option::of)
            .toCompletableFuture()
        ),
        Case($Success($()), command -> CompletableFuture.completedFuture(Option.of("Unknown command: " + command))),
        Case($Failure($()), ex -> CompletableFuture.completedFuture(Option.of("Was not able to get next command. (" + ex.getMessage() + ")")))
      );
    } while (output.get().isDefined());
  }

  private static Option<Integer> tryParseInt(String s) {
    return Option.of(s.replace("spawn ", "")).flatMap(count -> Try.of(() -> Integer.valueOf(count)).toOption());
  }

  private static HashMap<Coords, Integer> getHeatMapValues(List<Minion.Stopped> minionResults) {
    return minionResults.flatMap(stopped -> stopped.visited).foldLeft(HashMap.<Coords, Integer>empty(), (heatMap, coords) -> {
      final Integer newValue = heatMap.get(coords).getOrElse(0) + 1;
      return heatMap.put(coords, newValue);
    });
  }

  private static HashMap<Coords, Character> getHeatMapTiles(HashMap<Coords, Integer> heatMap) {
    final Integer hottestValue = heatMap.values().max().getOrElse(0);
    final List<Character> tiles = List.of('█', '▓', '▒', '░');
    return heatMap.mapValues(heat -> tiles.get((heat * tiles.size() - 1) / hottestValue));
  }

  private static List<String> getHeatMap(HashMap<Coords, Character> heatMap) {
    return maze.zipWithIndex().map(rowAndY -> {
      final String row = rowAndY._1();
      final Integer y = rowAndY._2();
      return List.ofAll(row.toCharArray()).zipWithIndex().map(tileAndX -> {
        final Integer x = tileAndX._2();
        final Coords coords = new Coords(x, y);
        return heatMap.get(coords).getOrElse('█');
      }).mkString();
    });
  }

}