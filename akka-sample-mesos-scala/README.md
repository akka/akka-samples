This tutorial is inspired from [akka-sample-cluster-scala](../akka-sample-cluster-scala).
 It contains a sample illustrating how to deploy an [Akka cluster](http://doc.akka.io/docs/akka/2.5/scala/cluster-usage.html) in Mesos with Marathon.

- Package an Akka Cluster App with Docker
- Deploy it in Mesos with Marathon
- Subscribe to cluster membership events
- Bring more nodes by scaling up the application 

## A Simple Cluster with Mesos

Open [mesos.conf](src/main/resources/mesos.conf)

To enable cluster capabilities in your Akka project you should, at a minimum, add the remote settings, and use `akka.cluster.ClusterActorRefProvider`. 
The `akka.cluster.seed-nodes` should normally also be added to your application.conf file.

The seed nodes are configured contact points which newly started nodes will try to connect with in order to join the cluster.

In Mesos the seed nodes can be discovered automatically, through the Mesos API, or through the Marathon API.
The property `akka.cluster.discovery.url` defines the URL to the API. This example builds on the Marathon API that list the running tasks for a particular application.

The Marathon application deploys one or more Docker containers in Mesos with the same Akka project. 
A sample API response listing the running tasks in Marathon is:

```json
{
  tasks: [
    {
      id: "akka-cluster.086db21b-7192-11e7-8203-0242ac107905",
      slaveId: "35f9af86-f5b0-4e95-a2b1-5f201b10fbaa-S0",
      host: "192.168.99.100",
      state: "TASK_RUNNING",
      startedAt: "2017-07-25T23:36:09.629Z",
      stagedAt: "2017-07-25T23:36:07.949Z",
      ports: [
        11696
      ],
      version: "2017-07-25T23:36:07.294Z",
      ipAddresses: [
        {
          ipAddress: "172.17.0.2",
          protocol: "IPv4"
        }
      ],
      appId: "/akka-cluster"
    }
  ]
}
```
* `host` is the IP of the machine (Mesos Agent) where the task is running. `akka.remote.netty.tcp.hostname` should be set to this IP.
* `ports` is a list with the ports exposed on the `host`. 
    The default port `2551` is mapped to port `11696` on the `host` in this example.
    `akka.remote.netty.tcp.port` is set to one of these ports. `akka.cluster.discovery.port-index` defines which port should be used.
* `ipAddress` is the IP address of the Docker container. `akka.remote.netty.tcp.bind-hostname` should be set to this IP.

To learn more about `bind-hostname` and `bind-port` vs `hostname` and `port` with Docker see [akka-behind-nat-or-in-a-docker-container](http://doc.akka.io/docs/akka/2.5/scala/remoting.html#akka-behind-nat-or-in-a-docker-container).

Based on this sample output from the Marathon API, the seed nodes would be:
 
    ["akka.tcp://my-mesos-cluster@192.168.99.100:11696"]
    
As more nodes are added in the cluster, more `seed-nodes` are configured. 
See [MarathonConfig.scala](src/main/scala/sample/cluster/mesos/MarathonConfig.scala) object.     

Open [SimpleMesosClusterApp.scala](src/main/scala/sample/cluster/mesos/SimpleMesosClusterApp.scala).

The small program together with its configuration starts an ActorSystem with the Cluster enabled. It joins the cluster and starts an actor that logs some membership events. Take a look at the [SimpleClusterListener.scala](src/main/scala/sample/cluster/mesos/SimpleClusterListener.scala) actor.

You can read more about the cluster concepts in the [documentation](http://doc.akka.io/docs/akka/2.5/scala/cluster-usage.html).

To run this sample you must first build the project and the docker container:
    
    $ sbt docker:publishLocal

This builds the image `akkasamples/akka-sample-mesos-scala:latest`

To test this with a local Mesos cluster run: 

    cd ./src/test/mesos
    DOCKER_IP=`docker-machine ip default` docker-compose up
    
This local example works only with Docker Machine. If you're using Docker for Mac it's very easy to setup a `docker-machine` too:

    $ docker-machine create -d virtualbox --engine-storage-driver=overlay2 default
    $ docker-machine start default
    
    # activate docker-machine
    $ eval "$(docker-machine env default)"
    
    # rebuild the project if the docker-machine wasn't used initially
    #   otherwise the image is pulled from Docker Hub
    $ sbt docker:publishLocal
        
Learn more about how to setup a new Docker Machine see [https://docs.docker.com/machine/](https://docs.docker.com/machine/) .        
    
Once the cluster is up and running you should have Mesos UI running at `http://192.168.99.100:5050/` and Marathon UI at `http://192.168.99.100:8080` ( assuming the IP of the docker-machine is `1892.168.99.100` )
    
To deploy the application in Marathon open [marathon-app-local.json](src/test/mesos/marathon-app-local.json). Edit the `AKKA_CLUSTER_DISCOVERY_URL` ENV VAR to match the IP of the `docker-machine`. You can view the IP by running `docker-machine ip default`.

    curl http://`docker-machine ip default`:8080/v2/apps/ --data @marathon-app-local.json -H 'Content-Type:application/json'   

Open Mesos UI to view the logs of the new running tasks. There should be 2 tasks running.
In the log output of the tasks you see that the cluster node have been started with status 'Up'. The 2 instances should have joined the cluster.

```
[main] [akka.remote.Remoting] Remoting started; listening on addresses :[akka.tcp://my-mesos-cluster@192.168.99.100:11579]
[main] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Starting up...
[main] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Registered cluster JMX MBean [akka:type=Cluster]
[main] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Started up successfully
[my-mesos-cluster-akka.actor.default-dispatcher-6] [akka.tcp://my-mesos-cluster@192.168.99.100:11579/system/cluster/core/daemon/downingProvider] Don't use auto-down feature of Akka Cluster in production. See 'Auto-downing (DO NOT USE)' section of Akka Cluster documentation.
[my-mesos-cluster-akka.actor.default-dispatcher-4] [akka.tcp://my-mesos-cluster@192.168.99.100:11579/user/clusterListener] Current members: 
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Received InitJoin message from [Actor[akka.tcp://my-mesos-cluster@192.168.99.100:11448/system/cluster/core/daemon/joinSeedNodeProcess-1#239312336]], but this node is not initialized yet
[my-mesos-cluster-akka.actor.default-dispatcher-6] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Received InitJoinNack message from [Actor[akka.tcp://my-mesos-cluster@192.168.99.100:11448/system/cluster/core/daemon#1933952657]] to [akka.tcp://my-mesos-cluster@192.168.99.100:11579]
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] is JOINING, roles []
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Leader is moving node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] to [Up]
[my-mesos-cluster-akka.actor.default-dispatcher-2] [akka.tcp://my-mesos-cluster@192.168.99.100:11579/user/clusterListener] Member is Up: akka.tcp://my-mesos-cluster@192.168.99.100:11579
[my-mesos-cluster-akka.actor.default-dispatcher-3] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Metrics collection has started successfully
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Received InitJoin message from [Actor[akka.tcp://my-mesos-cluster@192.168.99.100:11448/system/cluster/core/daemon/joinSeedNodeProcess-1#239312336]] to [akka.tcp://my-mesos-cluster@192.168.99.100:11579]
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Sending InitJoinAck message from node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] to [Actor[akka.tcp://my-mesos-cluster@192.168.99.100:11448/system/cluster/core/daemon/joinSeedNodeProcess-1#239312336]]
[my-mesos-cluster-akka.actor.default-dispatcher-5] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Node [akka.tcp://my-mesos-cluster@192.168.99.100:11448] is JOINING, roles []
[my-mesos-cluster-akka.actor.default-dispatcher-6] [akka.cluster.Cluster(akka://my-mesos-cluster)] Cluster Node [akka.tcp://my-mesos-cluster@192.168.99.100:11579] - Leader is moving node [akka.tcp://my-mesos-cluster@192.168.99.100:11448] to [Up]
[my-mesos-cluster-akka.actor.default-dispatcher-18] [akka.tcp://my-mesos-cluster@192.168.99.100:11579/user/clusterListener] Member is Up: akka.tcp://my-mesos-cluster@192.168.99.100:11448
```
These logs show 2 instances running on ports `11448` and `11579`.

Start even more nodes by going into Marathon UI and scale up the `akka-cluster` app. 

To see how members are removed from the cluster scale down the app in Marathon to fewer instanced. You should then see in the logs messages like:

```
Member is Removed: akka.tcp://my-mesos-cluster@192.168.99.100:11257 after Exiting
```

