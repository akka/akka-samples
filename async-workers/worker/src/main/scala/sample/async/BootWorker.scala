/**
 * Copyright (C) 2011 Typesafe, Inc. <http://typesafe.com>
 */

package sample.async

import akka.actor._
import akka.http._
import akka.config.Supervision._

class BootWorker {
  val factory =
    SupervisorFactory(
      SupervisorConfig(
        OneForOneStrategy(List(classOf[Exception]), 3, 100),
        Supervise(Actor.actorOf[Worker], Permanent) :: Nil))

  factory.newInstance.start

  // add graceful shutdown so that the worker runs postStop
  Runtime.getRuntime.addShutdownHook(
    new Thread(
      new Runnable {
        def run = {
          Actor.registry.shutdownAll
        }
      }))
}
