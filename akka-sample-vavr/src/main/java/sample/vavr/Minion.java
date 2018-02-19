package sample.vavr;

import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import io.vavr.Tuple;
import io.vavr.Tuple1;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.match.annotation.Patterns;
import io.vavr.match.annotation.Unapply;

import static io.vavr.API.*;
import static io.vavr.Patterns.*;
import static sample.vavr.MinionPatterns.*;
import static io.vavr.Predicates.instanceOf;

@Patterns
public class Minion extends UntypedAbstractActor {

  final private Maze maze;

  private Coords pos;
  private List<Coords> visited = List.empty();

  public Minion(Maze maze) {
    this.maze = maze;
  }

  public void onReceive(Object message) {
    Option<Command> nextCommand = Match(message).of(
      Case($Move($Some($(maze::isLegal))), c -> {
        if (this.pos != null) {
          visited = visited.append(this.pos);
        }
        this.pos = c.get();
        return Match(c.get()).of(
          Case($(maze::isFinish), Option.of(new Stuck())),
          Case($(), Option.of(new Move(maze.legalFrom(this.pos))))
        );
      }),

      Case($Move($Some($(visited::contains))), Option.of(new Stuck())),
      Case($Move($Some($())), Option.of(new Stuck())),
      Case($Move($None()), Option.of(new Stuck())),

      Case($(instanceOf(Stuck.class)), s -> {
        getContext().parent().tell(new Stopped(this.pos, this.visited), self());
        getContext().stop(self());
        return Option.none();
      })
    );

    nextCommand.forEach(command -> {
      getContext().self().tell(command, getContext().self());
    });
  }

  public static Props props(Maze maze) {
    return Props.create(Minion.class, maze);
  }

  interface Command {};

  static class Move implements Minion.Command {
    final Option<Coords> to;

    public Move(Option<Coords> offset) {
      this.to = offset;
    }

  }

  static class Stopped implements Minion.Command {
    final Coords at;
    final List<Coords> visited;

    public Stopped(Coords at, List<Coords> visited) {
      this.at = at;
      this.visited = visited;
    }

    public String toString() {
      return "Stopped[at=" + at.toString() + ", visited=" + visited.size() + "]";
    }

  }

  static class Stuck implements Minion.Command { }

  @Unapply
  static Tuple1<Option<Coords>> Move(Minion.Move move) {
    return Tuple.of(move.to);
  }

  @Unapply
  static Tuple2<Coords, List<Coords>> Stopped(Minion.Stopped stopped) {
    return Tuple.of(stopped.at, stopped.visited);
  }

}
