This tutorial contains a sample illustrating [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/cluster-sharding.html#an-example).

## Example overview

First make sure the correct settings in [application.conf](src/main/resources/application.conf) are set as described in the akka-sample-cluster tutorial.

### KillrWeather

Open [KillrWeather.scala](src/main/scala/sample/sharding/KillrWeather.scala).

This small program starts an ActorSystem with Cluster Sharding enabled. It joins the cluster and starts a `Guardian` actor for the system,
and a `TemperatureDevice` actor. The `TemperatureDevice` is an `Aggregator` that can aggregate any type of device data it receives,
and respond to queries against that data, for example high/low, average, etc.
The `Guardian` starts the infrastructure to shard device `Aggregator` instances, which in this simple sample is temperature devices.
Other types can easily be added.

### WeatherEdges

Open [WeatherEdges.scala](src/main/scala/sample/sharding/WeatherEdges.scala).

`WeatherEdges` is the program simulating many weather stations and their devices which read and report weather data to KillrWeather clusters.
This example shows temperature, though in the wild would have other device data points such
as pressure, wind speed, precipitation, etc. This program starts the infrastructure to shard `WeatherDevice` instances
and send data via HTTP to the cluster, using [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html), for processing and analytics.

## Running the samples

### KillrWeather Cluster Sharding

To run this sample, first type `sbt "runMain sample.sharding.KillrWeather"` if it is not already started.

`KillrWeather` starts three actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and then open three terminal windows.

In the first terminal window, start the first seed node with the following command:

    sbt "runMain sample.sharding.KillrWeather 2551"

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

You'll see a log message when `Devices` sends a message to record the current temperature, and for each of those you'll see a log message from the `Device` showing the action taken and the new average temperature.

In the second terminal window, start the second seed node with the following command:

    sbt "runMain sample.sharding.KillrWeather 2552"

2552 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'. Switch over to the first terminal window and see in the log output that the member joined.

Some of the devices that were originally on the `ActorSystem` on port 2551 will be migrated to the newly joined `ActorSystem` on port 2552. The migration is straightforward: the old actor is stopped and a fresh actor is started on the newly created `ActorSystem`. Notice this means the average is reset: if you want your state to be persisted you'll need to take care of this yourself. For this reason Cluster Sharding and Akka Persistence are such a popular combination.

Start another node in the third terminal window with the following command:

    sbt "runMain sample.sharding.KillrWeather 0"

Now you don't need to specify the port number, 0 means that it will use a random available port. It joins one of the configured seed nodes. Look at the log output in the different terminal windows.

Start even more nodes in the same way, if you like.

### WeatherEdges - weather stations

In another terminal start the `WeatherEdges` :

    sbt "runMain sample.sharding.WeatherEdges"
    
This will eventually attempt to connect to port 8081, a port opened by KillrWeather.    

### Shutting down

Shut down one of the nodes by pressing 'ctrl-c' in one of the terminal windows. The other nodes will detect the failure after a while, which you can see in the log output in the other terminals.
