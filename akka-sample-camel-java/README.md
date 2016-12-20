This tutorial contains 3 samples of [Akka Camel](http://doc.akka.io/docs/akka/2.4.14/java/camel.html).

- Asynchronous routing and transformation
- Custom Camel route
- Quartz scheduler

## Asynchronous routing and transformation

This example demonstrates how to implement consumer and producer actors that support [Asynchronous routing](http://doc.akka.io/docs/akka/2.4.14/java/camel.html#Asynchronous_routing) with their Camel endpoints. The sample application transforms the content of the Akka homepage, [http://akka.io](http://akka.io), by replacing every occurrence of *Akka* with *AKKA*.

To run this example, start the application main class by running `sbt "runMain sample.camel.http.HttpExample"` if it's not already started. Then direct the browser to [http://localhost:8875](http://localhost:8875) and the transformed Akka homepage should be displayed. Please note that this example will probably not work if you're behind an HTTP proxy.

The following figure gives an overview how the example actors interact with external systems and with each other. A browser sends a GET request to http://localhost:8875 which is the published endpoint of the HttpConsumeractor. The `HttpConsumer` actor forwards the requests to the [HttpProducer.java](src/main/java/sample/camel/http/HttpProducer.java) actor which retrieves the Akka homepage from http://akka.io. The retrieved HTML is then forwarded to the [HttpTransformer.java](src/main/java/sample/camel/http/HttpTransformer.java) actor which replaces all occurrences of *Akka* with *AKKA*. The transformation result is sent back the HttpConsumer which finally returns it to the browser.

![](tutorial/camel-async-interact.png)

Implementing the example actor classes and wiring them together is rather easy as shown in [HttpConsumer.java](src/main/java/sample/camel/http/HttpConsumer.java), [HttpProducer.java]((src/main/java/sample/camel/http/HttpProducer.java)) and [HttpTransformer.java]((src/main/java/sample/camel/http/HttpTransformer.java)).

The [jetty endpoints](http://camel.apache.org/jetty.html) of HttpConsumer and HttpProducer support asynchronous in-out message exchanges and do not allocate threads for the full duration of the exchange. This is achieved by using [Jetty continuations](http://wiki.eclipse.org/Jetty/Feature/Continuations) on the consumer-side and by using [Jetty's asynchronous HTTP client](http://wiki.eclipse.org/Jetty/Tutorial/HttpClient) on the producer side. The following high-level sequence diagram illustrates that.

![](tutorial/camel-async-sequence.png)

## Custom Camel route example

This section also demonstrates the combined usage of a RouteProducer and a RouteConsumeractor as well as the inclusion of a custom Camel route. The following figure gives an overview.

![](tutorial/camel-custom-route.png)

- A consumer actor receives a message from an HTTP client
- It forwards the message to another actor that transforms the message (encloses the original message into hyphens)
- The transformer actor forwards the transformed message to a producer actor
- The producer actor sends the message to a custom Camel route beginning at the `direct:welcome` endpoint
- A processor (transformer) in the custom Camel route prepends "Welcome" to the original message and creates a result message
- The producer actor sends the result back to the consumer actor which returns it to the HTTP client

The producer actor knows where to reply the message to because the consumer and transformer actors have forwarded the original sender reference as well. The application configuration and the route starting from direct:welcome are done in the code above.

To run this example, type `sbt "runMain sample.camel.route.CustomRouteExample"`

POST a message to `http://localhost:8877/camel/welcome`.

    curl -H "Content-Type: text/plain" -d "Anke" http://localhost:8877/camel/welcome

The response should be:

    Welcome - Anke -

## Quartz Scheduler Example

Here is an example showing how simple it is to implement a cron-style scheduler by using the Camel Quartz component in Akka.

Open [MyQuartzActor.java](src/main/java/sample/camel/quartz/MyQuartzActor.java).

The example creates a "timer" actor which fires a message every 2 seconds.

For more information about the Camel Quartz component, see here: [http://camel.apache.org/quartz.html](http://camel.apache.org/quartz.html)

