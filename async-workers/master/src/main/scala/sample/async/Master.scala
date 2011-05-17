/**
 * Copyright (C) 2011 Typesafe, Inc. <http://typesafe.com>
 */

package sample.async

import akka.actor._
import akka.config.Config
import akka.event.EventHandler
import akka.http._
import akka.util.duration._
import akka.util.Duration

/**
 * Master configuration.
 *
 * Settings can be specified through the master config file.
 */
object Master {
  val Name = Config.config.getString("master.name", "master")

  val DefaultTimeout = 3 seconds

  val Timeout = Config.config.getString("master.timeout") match {
    case Some(Duration(timeout)) => timeout
    case _ => DefaultTimeout
  }
}

/**
 * Master actor. In this sample the master uses next available delegation
 * where it uses the availability messages from delegates to work out
 * where it should delegate to next.
 *
 * Other possibilities are RandomDelegation and RoundRobinDelegation.
 *
 * See the delegation subproject for more about Delegations.
 */
class Master extends Actor with NextAvailableDelegation {
  self.id = Master.Name
}

/**
 * Mist endpoint for http interface. Creates a responder for each
 * incoming request.
 */
class MasterEndpoint extends Actor with Endpoint {
  self.dispatcher = Endpoint.Dispatcher

  def hook(uri: String) = true

  def provide(uri: String) = Actor.actorOf[Responder].start

  override def preStart = {
    Actor.registry.actorsFor(classOf[RootEndpoint]).head ! Endpoint.Attach(hook, provide)
  }

  def receive = handleHttpRequest
}

/**
 * Responder that receives http requests from Mist, sends requests to
 * the master delegation, and then waits for either a Timeout message
 * or the response.
 *
 * The timeout can be set in the master config file.
 */
class Responder extends Actor {
  val master = Actor.registry.actorsFor(classOf[Master]).head

  def receive = {
    case post: Post =>
      val input = post.request.getParameter("input")
      master ! Request(input, timeout = Master.Timeout)
      become(respond(post))
    case get: Get =>
      master ! Request("ereht iH", timeout = Master.Timeout)
      become(respond(get))
  }

  def respond(request: RequestMethod): Receive = {
    case Timeout =>
      EventHandler.debug(this, "Timeout")
      request OK "Timeout"
      self.stop()
    case response: String =>
      EventHandler.debug(this, "Response: " + response)
      request OK response
      self.stop()
  }
}
