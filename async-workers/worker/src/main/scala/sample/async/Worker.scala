/**
 * Copyright (C) 2011 Typesafe, Inc. <http://typesafe.com>
 */

package sample.async

import akka.actor._
import akka.config.Config
import akka.event.EventHandler
import akka.util.duration._
import akka.util.Duration
import scala.util.Random.{nextInt => random}

/**
 * Worker configuration.
 *
 * Settings can be specified through the worker config file.
 */
object Worker {
  val Name = Config.config.getString("worker.name", "worker")

  val DefaultHeartbeat = 5 seconds

  val Heartbeat = Config.config.getString("worker.heartbeat") match {
    case Some(Duration(heartbeat)) => heartbeat
    case _ => DefaultHeartbeat
  }

  val DefaultMaster = Address("master", "localhost", 9000)

  val Master = {
    val name = Config.config.getString("worker.master.name", DefaultMaster.name)
    val host = Config.config.getString("worker.master.host", DefaultMaster.host)
    val port = Config.config.getInt("worker.master.port", DefaultMaster.port)
    Address(name, host, port)
  }
}

/**
 * Worker actor. The worker has work delegated to it from the
 * master delegation. It uses a heartbeat to stay in touch with
 * the master node. See the Delegate code for more information.
 *
 * The master node address and the heartbeat timing can be set
 * through the worker akka.conf file.
 */
class Worker extends Actor with Delegate {
  self.id = Worker.Name

  val delegation = Worker.Master
  val heartbeatTiming = Worker.Heartbeat

  def respond = {
    case message: String =>
      val sleep = random(3500)
      EventHandler.debug(this, "Sleeping for %s ms" format sleep)
      Thread.sleep(sleep)
      val response = message.reverse
      EventHandler.debug(this, "Response: " + response)
      self reply response
  }
}
