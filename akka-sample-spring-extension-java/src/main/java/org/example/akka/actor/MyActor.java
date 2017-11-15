package org.example.akka.actor;

import javax.inject.Named;

import org.example.akka.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;

import akka.actor.AbstractActor;

@Named("myActor")
@Scope("prototype")
public class MyActor extends AbstractActor {

	@Autowired
	private MyActorService myActorService;

	private String attribute;
	private Integer attribute2;

	public MyActor(String attribute, Integer attribute2) {
		super();
		this.attribute = attribute;
		this.attribute2 = attribute2;
	}

	public MyActor() {
		this.attribute = "Default value";
		this.attribute2 = Integer.MAX_VALUE;
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(Message.class, msg -> {
			myActorService.printService(getSelf().path() + " " + attribute + " " + attribute2);
		}).build();
	}
}
