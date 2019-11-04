akka-sample-persistence-dc-java
===============================

## How to run

1. In terminal 1: `mvn exec:java -Dexec.mainClass="sample.persistence.multidc.ThumbsUpApp" -Dexec.args="cassandra"`

1. In terminal 2: `mvn exec:java -Dexec.mainClass="sample.persistence.multidc.ThumbsUpApp" -Dexec.args="22551 eu-west"`

1. In terminal 2: `mvn exec:java -Dexec.mainClass="sample.persistence.multidc.ThumbsUpApp" -Dexec.args="22552 eu-central"`

1. In terminal 4:
    * To add a thumbs-up for resource `akka` from user `u1` in DC `eu-west`: `curl -X POST http://127.0.0.1:22551/thumbs-up/akka/u1`
    * To add a thumbs-up for resource `akka` from user `u2` in DC `eu-west`: `curl -X POST http://127.0.0.1:22552/thumbs-up/akka/u2`
    * To get the users that gave thumbs-up for resource `akka`: `curl http://127.0.0.1:22552/thumbs-up/akka`
    * Note the port numbers 22551 for eu-west and 22552 for eu-central
