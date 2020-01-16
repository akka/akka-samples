## Experimenting with the example

When running the appliction without parameters it runs a six node cluster within the same JVM and starts a Cassandra database. It can be more interesting to run them in separate processes. Open four terminal windows.

In the first terminal window, start the Cassandra database with the following command:

```bash 
sbt "runMain worker.Main cassandra"
```

The Cassandra database will stay alive as long as you do not kill this process, when you want to stop it you can do that with `Ctrl + C`. Without the database the back-end nodes will not be able to start up.

You could also run your own local installation of Cassandra given that it runs on the default port on localhost and does not require a password. 


With the database running, go to the second terminal window and start the first seed node with the following command:

```bash
sbt "runMain worker.Main 2551"
```

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

In the third terminal window, start the front-end node with the following command:

```bash
sbt "runMain worker.Main 3001"		
```

3001 is to the port of the node. In the log output you see that the cluster node has been started and joins the 2551 node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the second terminal window and see in the log output that the member joined. So far, no `Worker` has not been started, i.e. jobs are produced and accepted but not processed.

In the fourth terminal window, start a worker node with the following command:

```bash
sbt "runMain worker.Main 5001 3"
```

5001 means the node will be a worker node, and the second parameter `3` means that it will host three worker actors.

Look at the log output in the different terminal windows. In the second window (front-end) you should see that the produced jobs are processed and logged as `"Consumed result"`.

Take a look at the logging that is done in `WorkProducer`, `Master` and `Worker`. Identify the corresponding log entries in the 3 terminal windows with Akka nodes.

Shutdown the worker node (fourth terminal window) with `ctrl-c`. Observe how the `"Consumed result"` logs in the front-end node (second terminal window) stops. Start the worker node again.

```bash
sbt "runMain worker.Main 5001 3"
```

You can also start more such worker nodes in new terminal windows.

You can start more cluster back-end nodes using port numbers between 2000-2999. 

```bash
sbt "runMain worker.Main 2552"
```

The nodes with port 2551 to 2554 are configured to be used as "seed nodes" in this sample, if you shutdown all or start none of these the other nodes will not know how to join the cluster. If all four are shut down and 2551 is started it will join itself and form a new cluster. 

As long as one of the four nodes is alive the cluster will keep working. You can read more about this in the [Akka documentation section on seed nodes](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html).

You can start more cluster front-end nodes using port numbers between 3000-3999:

```bash
sbt "runMain worker.Main 3002"		
```

Any port outside these ranges creates a worker node, for which you can also play around with the number of worker actors on using the second parameter. 

```bash
sbt "runMain worker.Main 5009 4"		
```

## The journal 

The files of the Cassandra database are saved in the target directory and when you restart the application the state is recovered. You can clean the state with:

```bash
sbt clean
```