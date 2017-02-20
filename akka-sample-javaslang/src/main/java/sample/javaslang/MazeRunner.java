package sample.javaslang;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import javaslang.collection.List;
import javaslang.control.Option;
import javaslang.control.Try;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static javaslang.API.*;
import static javaslang.Patterns.*;

public class MazeRunner {

  public static final void main(String[] args) {

    final List<String> maze = List.of(
      "┏━━━━━━┓",
      "┃      ┃",
      "┃     ⚑┃",
      "┗━━━━━━┛"
    );

    ActorSystem sys = ActorSystem.create("MazeRunner");
    ActorRef master = sys.actorOf(Props.create(Master.class, () -> new Master(new Maze(maze))));

    repl(master);

    sys.terminate();
  }

  private static void repl(ActorRef master) {
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    Option<String> output = Option.none();

    do {
      System.out.println(output.getOrElse(""));
      System.out.print("> ");
      output = Match(Try.of(br::readLine)).of(
        Case(Success($("q")), Option.none()),
        Case(Success($(s -> s.startsWith("spawn "))), s ->
          Match(Option.of(s.replace("spawn ", "")).flatMap(count -> Try.of(() -> Integer.valueOf(count)).toOption())).of(
            Case(Some($()), i -> {
              master.tell(new Master.Start(i), ActorRef.noSender());
              return Option.of("Spawning " + i + " minions.");
            }),
            Case(None(), Option.of("Unable to parse minion count to spawn."))
          )
        )
      );
    } while (output.isDefined());
  }

}