akka-sample-persistence-dc-java
===============================

## How to run

1. Setup Cassandra
   * Either start a local Cassnadsra listening on port 9042 or in terminal 1: `mvn exec:java -Dexec.mainClass="sample.persistence.res.MainApp" -Dexec.args="cassandra"`

1. In terminal 2: `mvn compile exec:java -Dexec.mainClass="sample.persistence.res.MainApp" -Dexec.args="2551 eu-west"`

1. In terminal 3: `mvn compile exec:java -Dexec.mainClass="sample.persistence.res.MainApp" -Dexec.args="2552 eu-central"`

1. In terminal 4:
    * To add a thumbs-up for resource `akka` from user `u1` in DC `eu-west`: `curl -X POST http://127.0.0.1:22551/thumbs-up/akka/u1`
    * To add a thumbs-up for resource `akka` from user `u2` in DC `eu-west`: `curl -X POST http://127.0.0.1:22552/thumbs-up/akka/u2`
    * To get the users that gave thumbs-up for resource `akka`: `curl http://127.0.0.1:22552/thumbs-up/akka`
    * Note the port numbers 22551 for eu-west and 22552 for eu-central

---

The Akka family of projects is managed by teams at Lightbend with help from the community.

License
-------

Akka is licensed under the Business Source License 1.1, please see the [Akka License FAQ](https://www.lightbend.com/akka/license-faq).