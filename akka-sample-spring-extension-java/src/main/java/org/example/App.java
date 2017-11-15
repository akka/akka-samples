package org.example;

import org.example.akka.di.SpringExtension;
import org.example.akka.message.Message;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

@SpringBootApplication
public class App {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
		ActorSystem system = context.getBean("actorSystem", ActorSystem.class);
		SpringExtension springExtension = context.getBean("springExtension", SpringExtension.class);

		// Using constructor with parameters.
		ActorRef myActor1 = system.actorOf(springExtension.props("myActor", "Parametrized", 100), "MyActor1");
		myActor1.tell(new Message(), ActorRef.noSender());

		// Using constructor with no parameters
		ActorRef myActor2 = system.actorOf(springExtension.props("myActor"), "MyActor2");
		myActor2.tell(new Message(), ActorRef.noSender());

	}
}
