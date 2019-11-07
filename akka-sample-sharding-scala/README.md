# Cluster Sharding sample

The KillrWeather sample illustrates how to use [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/typed/cluster-sharding.html).
It also shows the basic usage of [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html).
 
## KillrWeather

Open [KillrWeather.scala](killrweather/src/main/scala/sample/killrweather/KillrWeather.scala).
This program starts an ActorSystem with Cluster Sharding enabled. It joins the cluster and starts a `Guardian` actor for the system. 

### Guardian

The [Guardian.scala](killrweather/src/main/scala/sample/killrweather/Guardian.scala) starts the infrastructure to shard any number of data `Aggregator`
types on each clustered node. 

### Aggregator - sharded data by type
 
A sharded `Aggregator` has a declared data type and receives that data stream from remote devices via the  `Guardian`.
For each `Aggregator`, for one or across all weather stations, common cumulative computations can be run 
for a given time window queried, e.g. daily, monthly or annual such as:

* aggregate
* averages 
* high/low 
* topK (e.g. the top N highest temperatures)

Thus far, only temperature data is received, sharded and processed. Other types can easily be added.

### Receiving edge device data by data type

A [WeatherServer](killrweather/src/main/scala/sample/killrweather/WeatherServer.scala) is started with
HTTP [WeatherRoutes](killrweather/src/main/scala/sample/killrweather/WeatherRoutes.scala) 
to receive and unmarshall data from remote devices by data type, by station ID, device ID and data type.
The data types are sharded using [Akka Cluster Sharding](http://doc.akka.io/docs/akka/current/scala/typed/cluster-sharding.html
).

### Configuration

This application is configured in [killrweather/src/main/resources/application.conf](killrweather/src/main/resources/application.conf)
Before running, first make sure the correct settings are set for your system, as described in the akka-sample-cluster tutorial.

## Fog Network

Open [Fog.scala](killrweather-fog/src/main/scala/sample/killrweather/fog/Fog.scala).

`Fog` is the program simulating many weather stations and their devices which read and report data to clusters.
The name refers to [Fog computing](https://en.wikipedia.org/wiki/Fog_computing) with edges - the remote weather station
nodes and their device edges.
This example starts simply with one device per station, and one data type, temperature. In the wild, other devices would include:
pressure, precipitation, wind speed, wind direction, sky condition and dewpoint.
`Fog` starts the [configured](#configuration) number of weather stations their devices.

### Weather stations and devices

Each [WeatherStation](killrweather-fog/src/main/scala/sample/killrweather/fog/WeatherStation.scala) is run on a task to trigger scheduled data sampling.
These samples are timestamped and sent to the cluster, via the `WeatherStation` [WeatherApi](killrweather-fog/src/main/scala/sample/killrweather/fog/WeatherApi.scala).
This is done over HTTP using [Akka HTTP](https://doc.akka.io/docs/akka-http/current/index.html). For simplicity, HTTP versus HTTPS is shown.

### Configuration

This application is configured in [killrweather-fog/src/main/resources/application.conf](killrweather-fog/src/main/resources/application.conf)

## Akka HTTP example

Within KillrWeather are two simple sides to an HTTP equation.

**Client**

* [WeatherApi](killrweather-fog/src/main/scala/sample/killrweather/fog/Fog.scala) - HTTP data marshall and send

**Server**

* [WeatherServer](killrweather/src/main/scala/sample/killrweather/WeatherServer.scala) - HTTP server
* [WeatherRoutes](killrweather/src/main/scala/sample/killrweather/WeatherRoutes.scala) - HTTP routes receiver which will unmarshall and pass on the data

## Running the samples

### The KillrWeather Cluster

There are two ways to run the cluster, the first is a convenience quick start.

#### A simple three node cluster in the same JVM

The simplest way to run this sample is to run this in a terminal, if not already started:
   
    sbt killrweather/run
   
This command starts three (the default) `KillrWeather` actor systems (a three node cluster) in the same JVM process. 

#### Dynamic WeatherServer ports

In the log snippet below, note the dynamic weather ports opened by each KillrWeather node's `WeatherServer` for weather stations to connect to. 
The number of ports are by default three, for the minimum three node cluster. You can start more cluster nodes, so these are dynamic to avoid bind errors. 
```
[2019-11-04 14:43:45,861] [INFO] [akka.actor.typed.ActorSystem] [KillrWeather-akka.actor.default-dispatcher-3] [] - WeatherServer online at http://127.0.0.1:8033/
[2019-11-04 14:43:45,861] [INFO] [akka.actor.typed.ActorSystem] [KillrWeather-akka.actor.default-dispatcher-14] [] - WeatherServer online at http://127.0.0.1:8056/
[2019-11-04 14:43:45,861] [INFO] [akka.actor.typed.ActorSystem] [KillrWeather-akka.actor.default-dispatcher-16] [] - WeatherServer online at http://127.0.0.1:8081/
```

#### A three node cluster in separate JVMs

It is more interesting to run them in separate processes. Stop the application and then open three terminal windows.
In the first terminal window, start the first seed node with the following command:

    sbt "killrweather/runMain sample.killrweather.KillrWeather 2553"

2553 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

You'll see a log message when a `WeatherStation` sends a message to record the current temperature, and for each of those you'll see a log message from the `WeatherRoutes` showing the action taken and the new average temperature.

In the second terminal window, start the second seed node with the following command:

    sbt "killrweather/runMain sample.killrweather.KillrWeather 2554"

2554 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'. Switch over to the first terminal window and see in the log output that the member joined.

Some of the temperature aggregators that were originally on the `ActorSystem` on port 2553 will be migrated to the newly joined `ActorSystem` on port 2554. The migration is straightforward: the old actor is stopped and a fresh actor is started on the newly created `ActorSystem`. Notice this means the average is reset: if you want your state to be persisted you'll need to take care of this yourself. For this reason Cluster Sharding and Akka Persistence are such a popular combination.

Start another node in the third terminal window with the following command:

    sbt "killrweather/runMain sample.killrweather.KillrWeather 0"

Now you don't need to specify the port number, 0 means that it will use a random available port. It joins one of the configured seed nodes.
Look at the log output in the different terminal windows.

Start even more nodes in the same way, if you like.

#### Dynamic WeatherServer port

Each node's log will show its dynamic weather port opened for weather stations to connect to. 
```
[2019-11-04 14:43:45,861] [INFO] [akka.actor.typed.ActorSystem] [KillrWeather-akka.actor.default-dispatcher-16] [] - WeatherServer online at http://127.0.0.1:8081/
```

### The Fog Network
 
In a new terminal start the `Fog`, (see [Fog computing](https://en.wikipedia.org/wiki/Fog_computing))

Each simulated remote weather station will attempt to connect to one of the round-robin assigned ports for Fog networking over HTTP.   
Similar to the dynamic WeatherServer ports **8081 8033 8056**  [in the log snippet above](#dynamic-weatherserver-ports),  
pass in the ports you see (8081 is always first), for example:
 
    sbt "killrweather-fog/runMain sample.killrweather.fog.Fog 8081 8033 8056"
     
### Shutting down

Shut down one of the nodes by pressing 'ctrl-c' in one of the terminal windows. The other nodes will detect the failure after a while, which you can see in the log output in the other terminals.
