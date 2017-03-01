package sample.javaslang;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import javaslang.collection.List;
import javaslang.collection.Stream;
import javaslang.control.Option;

import static javaslang.API.*;
import static javaslang.Predicates.*;

public class Master extends UntypedAbstractActor {

  private final Maze maze;

  private List<Minion.Stopped> results = List.empty();

  public Master(Maze maze) {
    this.maze = maze;
  }

  public void onReceive(Object message) {
    Match(message).of(
      Case(instanceOf(Start.class), s -> run(() ->
        Stream.range(0, s.count).forEach(c ->
          getContext()
            .actorOf(Minion.props(maze))
            .tell(new Minion.Move(Option.of(new Coords(1, 1))), ActorRef.noSender())
        )
      )),
      Case(instanceOf(Minion.Stopped.class), stopped -> run(() ->
        results = results.prepend(stopped)
      )),
      Case(instanceOf(GetResults.class), r -> run(() ->
        sender().tell(results, self())
      ))
    );
  }

  public static Props props(Maze maze) {
    return Props.create(Master.class, maze);
  }

  static class Start {
    public Integer count;
    public Start(Integer count) {
      this.count = count;
    }
  }

  static class GetResults {}
}
