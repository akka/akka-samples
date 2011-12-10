
Async Workers Sample
====================

This sample project requires [sbt] version 0.11.2

[sbt]: http://github.com/harrah/xsbt


Building
--------

Run `sbt package dist` to create Akka microkernel distributions for master and
worker nodes:

    sbt package dist

The `master/target/dist` and `worker/target/dist` directories contain
stand-alone Akka microkernels. For multiple worker nodes copy the worker dist
and modify the config as needed.


Running
-------

Start the microkernels using the scripts in `bin`. For example, run in two
different shells:

    master/target/dist/bin/start

and

    worker/target/dist/bin/start

You can then go to http://localhost:9090 to send a GET request to the master
node and receive a greeting (or a timeout) in return.

Or POST to http://localhost:9090 with an `input` parameter and you'll receive
the input reversed. For example:

    curl -d "input=test" http://localhost:9090
