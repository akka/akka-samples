package sample.hello;

import akka.actor.AbstractActor;

public class Greeter extends AbstractActor {

  public static enum Msg {
    GREET, DONE;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchEquals(Msg.GREET, m -> {
        System.out.println("Hello World!");
        sender().tell(Msg.DONE, self());
      })
      .build();
  }
}
