# Cluster Client with gRPC transport

The purpose of this example is to illustrate how the deprecated
[Akka Cluster Client](https://doc.akka.io/docs/akka/2.5/cluster-client.html)
can be replaced by [Akka gRPC](https://doc.akka.io/docs/akka-grpc/current/index.html).

The example is intended to be copied and adjusted to your needs. It will not be
provided as a published artifact.

The example is still using an actor on the client side to have an API that is very close
to the original Cluster Client. The messages this actor can handle corresponds to the
[Distributed Pub Sub](https://doc.akka.io/docs/akka/current/distributed-pub-sub.html)
messages on the server side, such as `ClusterClient.Send`.

The `ClusterClient` actor delegates those messages to the gRPC client and on the
server side those are translated and delegated to the destination actors that
are registered via the `ClusterClientReceptionist` in the same way as in the original.

The application specific messages are wrapped and serialized with Akka serialization.

A more decoupled, and therefore better, solution would be to use Akka gRPC directly
and define an application specific protocol buffer messages and gRPC service calls.
Then the clients don't even have to be using Akka. If you are building a new
application that is recommended, but if you are already using Akka Cluster Client
the approach illustrated here is a good migration path that requires minimal changes
to existing source code.

## Single request-reply

For request-reply interactions when there is only one reply message for each request
it is more efficient to use the `ClusterClient.AskSend` message instead of
`ClusterClient.Send` as illustrated in the example. Then it doesn't have to
setup a full bidirectional gRPC stream for each request but can use the `Future`
based API.

## Initial contact points

Initial contact points and re-establishing connections is covered by
[Service Discovery in Akka gRPC](https://doc.akka.io/docs/akka-grpc/current/client/configuration.html)
which shows both configuration and programmatic methods.

## Failure detection

Heartbeat messages and failure detection of the connections have been removed
since that should be handled by the gRPC connections.




