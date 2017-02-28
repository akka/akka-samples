package sample.hello;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import static sample.hello.Greeter.Msg;

public class HelloWorld extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .matchEquals(Msg.DONE, m -> {
        // when the greeter is done, stop this actor and with it the application
        getContext().stop(self());
      })
      .build();
  }

  @Override
  public void preStart() {
    // create the greeter actor
    final ActorRef greeter = getContext().actorOf(Props.create(Greeter.class), "greeter");
    // tell it to perform the greeting
    greeter.tell(Msg.GREET, self());
  }
}
