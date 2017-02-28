package sample.hello;

import akka.actor.*;

public class Main2 {

  public static void main(String[] args) {
    ActorSystem system = ActorSystem.create("Hello");
    ActorRef a = system.actorOf(Props.create(HelloWorld.class), "helloWorld");
    system.actorOf(Props.create(Terminator.class, a), "terminator");
  }

  public static class Terminator extends AbstractLoggingActor {

    private final ActorRef ref;

    public Terminator(ActorRef ref) {
      this.ref = ref;
      getContext().watch(ref);
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
        .match(Terminated.class, t -> {
          log().info("{} has terminated, shutting down system", ref.path());
          getContext().system().terminate();
        })
        .build();
    }
  }
}
