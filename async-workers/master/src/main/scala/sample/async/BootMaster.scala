/**
 * Copyright (C) 2011 Typesafe, Inc. <http://typesafe.com>
 */

package sample.async

import akka.actor._
import akka.http._
import akka.config.Supervision._

class BootMaster {
  val supervisor =
    SupervisorFactory(
      SupervisorConfig(
        OneForOneStrategy(List(classOf[Exception]), 3, 100),
        Supervise(Actor.actorOf[Master], Permanent) ::
        Supervise(Actor.actorOf[RootEndpoint], Permanent) ::
        Supervise(Actor.actorOf[MasterEndpoint], Permanent) ::
        Nil))

  supervisor.newInstance.start
}
