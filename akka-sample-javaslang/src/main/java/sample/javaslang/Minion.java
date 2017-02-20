package sample.javaslang;

import akka.actor.UntypedAbstractActor;
import javaslang.Tuple;
import javaslang.Tuple1;
import javaslang.Tuple2;
import javaslang.collection.List;
import javaslang.control.Option;
import javaslang.match.annotation.Patterns;
import javaslang.match.annotation.Unapply;

import static javaslang.Patterns.Some;
import static sample.javaslang.Minion_MovePatterns.*;

import static javaslang.API.*;
import static javaslang.Patterns.*;
import static javaslang.Predicates.*;

public class Minion extends UntypedAbstractActor {

  final private Maze maze;

  private Coords pos;
  private List<Coords> visited;

  public Minion(Maze maze) {
    this.maze = maze;
  }

  public void onReceive(Object message) {
    Option<Command> nextCommand = Match(message).of(
      Case(Move(Some($(maze::isLegal))), c -> {
        visited = visited.append(this.pos);
        this.pos = c.get();
        return Match(c.get()).of(
          Case($(maze::isFinish), Option.of(new Stuck())),
          Case($(), Option.of(new Move(maze.legalFrom(this.pos))))
        );
      }),

      Case(Move(Some($(c -> visited.contains(c)))), Option.of(new Stuck())),
      Case(Move(Some($())), Option.of(new Stuck())),
      Case(Move(None()), Option.of(new Stuck())),

      Case(instanceOf(Stuck.class), s -> {
        getContext().parent().tell(new Stopped(this.pos, this.visited), self());
        getContext().stop(self());
        return Option.none();
      })
    );

    nextCommand.forEach(command -> {
      getContext().self().tell(command, getContext().self());
    });
  }

  interface Command {};

  @Patterns
  static class Move implements Command {
    final private Option<Coords> to;

    public Move(Option<Coords> offset) {
      this.to = offset;
    }

    @Unapply
    static Tuple1<Option<Coords>> Move(Move move) {
      return Tuple.of(move.to);
    }
  }

  static class Stuck implements Command {
  }

  @Patterns
  static class Stopped implements Command {
    final private Coords at;
    final private List<Coords> visited;

    public Stopped(Coords at, List<Coords> visited) {
      this.at = at;
      this.visited = visited;
    }

    @Unapply
    static Tuple2<Coords, List<Coords>> Stopped(Stopped stopped) {
      return Tuple.of(stopped.at, stopped.visited);
    }
  }
}
