package org.example.akka.actor;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Service;

@Service
public class MyActorService {

	public MyActorService() {

	}

	@PostConstruct
	public void init() {
		System.out.println("Created service as bean");
	}

	public void printService(String string) {
		System.out.println("Printed with bean: " + string);
	}
}
