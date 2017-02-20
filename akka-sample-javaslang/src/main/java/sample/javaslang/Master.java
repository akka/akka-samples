package sample.javaslang;

import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import javaslang.collection.List;
import javaslang.collection.Stream;

import static sample.javaslang.Minion_StoppedPatterns.*;

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
      Case(instanceOf(Start.class), s -> {
        Stream.range(0, s.count).forEach(c ->
          getContext().actorOf(Props.create(() -> new Minion(this.maze)))
        );
        return null;
      }),
      Case(instanceOf(Minion.Stopped.class), stopped -> {
        results = results.prepend(stopped);
        return null;
      }),
      Case(instanceOf(GetResults.class), r -> {
        sender().tell(results, self());
        return null;
      })
    );
  }

  private void startMinions() {

  }

  static class Start {
    public Integer count;
    public Start(Integer count) {
      this.count = count;
    }
  }

  static class GetResults {}
}
