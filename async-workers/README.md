
Async Workers Sample
====================

This sample project requires [sbt] (version 0.7.7)

[sbt]: http://code.google.com/p/simple-build-tool/


Building
--------

To build the code first `sbt update` to fetch dependencies:

    sbt update

Then run `sbt dist` to create Akka microkernel distributions for master and
worker nodes:

    sbt dist

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

There is a simple ajax form in `web/index.html` that can be used to send
requests to the master node via http post.
