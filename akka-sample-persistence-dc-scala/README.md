Akka Replicated Event Sourcing Multi DC Sample
=====================================

This is an example project demonstrating [Akka Replicated Event Sourcing](https://doc.akka.io/docs/akka/current/typed/replicated-eventsourcing.html)
to run a replica per datacenter.

## How to run

1. In terminal 1: `sbt "run cassandra"`

1. In terminal 2: `sbt "run 2551 eu-west"`

1. In terminal 3: `sbt "run 2552 eu-central"`

1. In terminal 4:
    * To add a thumbs-up for resource `akka` from user `u1` in DC `eu-west`: `curl -X POST http://127.0.0.1:22551/thumbs-up/akka/u1`
    * To add a thumbs-up for resource `akka` from user `u2` in DC `eu-west`: `curl -X POST http://127.0.0.1:22552/thumbs-up/akka/u2`
    * To get the users that gave thumbs-up for resource `akka`: `curl http://127.0.0.1:22552/thumbs-up/akka`
    * Note the port numbers 22551 for eu-west and 22552 for eu-central
