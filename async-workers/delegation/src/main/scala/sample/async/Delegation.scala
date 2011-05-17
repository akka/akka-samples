/**
 * Copyright (C) 2011 Typesafe, Inc. <http://typesafe.com>
 */

package sample.async

import akka.actor._
import akka.event.EventHandler
import akka.util.Duration
import scala.collection.mutable
import Connection.connection

// ------------------------------------------------------------
// Remote actor addresses
// ------------------------------------------------------------

/**
 * An address abstraction for remote actors so that only
 * server-managed remote actors are used.
 */
case class Address(name: String, host: String, port: Int) {
  def ! (message: Any)(implicit sender: Option[ActorRef]) = {
    Actor.remote.actorFor(name, host, port) ! message
  }
}

// ------------------------------------------------------------
// Delegation messages
// ------------------------------------------------------------

// internal delegation mesages

case class Heartbeat(delegate: Address)
case class Available(delegate: Address)
case class Unavailable(delegate: Address)

// api messages

/**
 * Delegate a one-way message. No reply expected.
 */
case class Send(message: Any)

/**
 * Delegate a request message where an async response is expected.
 * Optional timeout can be specified. If the request times out
 * then a Timeout message well be sent instead of a response.
 */
case class Request(message: Any, timeout: Duration = Duration.Inf)

/**
 * Timeout message to indicate that a request has timed out.
 */
case object Timeout


// ------------------------------------------------------------
// Delegation actors
// ------------------------------------------------------------

/**
 * The delegation (master) that coordinates everything.
 *
 * Different delegation strategies are available:
 * random, round robin, or next available.
 */
trait Delegation extends Actor {
  // a buffer for delegators for when there are no delegates currently available
  private val pending = mutable.Buffer.empty[ActorRef]
  private def dequeue(): ActorRef = pending.remove(0)
  private def enqueue(delegator: ActorRef) = pending += delegator
  private def removePending(delegator: ActorRef) = pending -= delegator

  // always register as a remote actor
  override def preStart = {
    Actor.remote.register(self.id, self)
  }

  def receive = {
    case Heartbeat(delegate) => heartbeat(delegate)
    case Available(delegate) => available(delegate)
    case Unavailable(delegate) => unavailable(delegate)
    case Send(message) => delegate(message)
    case Request(message, timeout) => delegate(message, self.sender, timeout)
    case Timeout => expire(self.sender)
  }

  /**
   * Heartbeat from delegate.
   */
  def heartbeat(delegate: Address) = {
    // do nothing
  }

  /**
   * A delegate is signalling their availability. If we have queued
   * delegators waiting for delegates then send them the next delegate now.
   */
  def available(delegate: Address) = {
    EventHandler.debug(this, "Available: " + delegate)
    add(delegate)
    while (!pending.isEmpty && hasNext) {
      dequeue() ! next()
    }
  }

  /**
   * A delegate is no longer available for work.
   */
  def unavailable(delegate: Address) = {
    EventHandler.debug(this, "Unavailable: " + delegate)
    remove(delegate)
  }

  /**
   * Delegate work to the next delegate according to the concrete delegation
   * implementation. If there is no next delegate then enqueue the delegator
   * until one becomes available. It's possible that the delegator will time
   * out before a delegate is available.
   */
  def delegate(message: Any, respondTo: Option[ActorRef] = None, timeout: Duration = Duration.Inf) = {
    val delegator = Actor.actorOf(new Delegator(message, respondTo, timeout, self)).start
    if (hasNext) delegator ! next()
    else enqueue(delegator)
  }

  /**
   * A delegator has expired, remove it if it's still pending.
   */
  def expire(delegator: Option[ActorRef]) = {
    delegator foreach removePending
  }

  // to be implemented by Delegations
  def delegates: Iterable[Address]
  def add(delegate: Address): Unit
  def remove(delegate: Address): Unit
  def hasNext: Boolean
  def next(): Address
}

/**
 * Delegator coordinates sending messages to, and receiving messages from,
 * a delegate. A unique delegator is started for each delegated message.
 *
 * The delegator first waits for the delegate to be assigned (there may
 * not have been a delegate available at the time of creation).
 *
 * If there is a respondTo actor and an expiry is set, then this timeout is
 * started immediately, even if an actual delegate has not yet been assigned.
 * The delegator will either receive the timeout or the response message first,
 * and passes this on to the respondTo actor.
 *
 * If a timeout is received then a Timeout message is also sent back to the
 * delegation so that the delegation can take action if needed.
 *
 * There is no failure recovery if the delegate is unreachable. This is simply
 * considered a timeout situation and the client should retry.
 */
class Delegator(message: Any, respondTo: Option[ActorRef], expiry: Duration, delegation: ActorRef) extends Actor {

  // scheduling, receiving, and cancelling timeouts

  val scheduledTimeout =
    if (respondTo.isDefined && expiry.isFinite) {
      Some(Scheduler.scheduleOnce(() => self ! Timeout, expiry.length, expiry.unit))
    } else None

  def receiveTimeout: Receive = {
    case Timeout =>
      respondTo foreach (_ ! Timeout)
      delegation ! Timeout
      self.stop()
  }

  def cancelTimeout() = scheduledTimeout foreach (_.cancel(false))

  /**
   * First wait for the delegate to be assigned or otherwise time out.
   * Once we've delegated we can wait for the response or otherwise time out.
   */
  def receive = receiveDelegate orElse receiveTimeout

  def receiveDelegate: Receive = {
    case delegate: Address =>
      EventHandler.debug(this, "Delegating to: " + delegate)
      connection {
        delegate ! message
        if (respondTo.isDefined) become(waitForResponse)
        else self.stop()
      } otherwise {
        EventHandler.debug(this, "Failed connection to: " + delegate)
        if (expiry.isFinite) respondTo foreach (_ ! Timeout)
        delegation ! Unavailable(delegate)
        cancelTimeout()
        self.stop()
      }
  }

  def waitForResponse = receiveTimeout orElse receiveResponse

  def receiveResponse: Receive = {
    case response =>
      respondTo foreach (_ ! response)
      cancelTimeout()
      self.stop()
  }
}

/**
 * Delegate trait to be implemented by the worker. Takes care of
 * coordinating availability with the delegation (master).
 *
 * A heartbeat is used to determine whether the master delegation
 * node is still available, and reconnects are tried if there are
 * connection failures.
 *
 * There is currently no failure recovery when trying to respond
 * to the delegator. The work is considered lost, and either the
 * delegator or the client are expected to time out.
 */
trait Delegate extends Actor {
  // create the address for this delegate
  lazy val address = {
    val remoteAddress = Actor.remote.address
    Address(self.id, remoteAddress.getHostName, remoteAddress.getPort)
  }

  // availability and heartbeat messages for this delegate
  lazy val available = Available(address)
  lazy val heartbeat = Heartbeat(address)

  // is this delegate considered connected to the delegation?
  var connected = false

  /**
   * Address of the delegation, to be specified by delegate impl.
   */
  def delegation: Address

  /**
   * The heartbeat timing, to be specified by delegate impl.
   */
  def heartbeatTiming: Duration

  override def preStart = {
    Actor.remote.register(self.id, self)
    self ! heartbeat
  }

  /**
   * Schedule a new Heartbeat message.
   */
  def nextHeartbeat() = {
    Scheduler.scheduleOnce(() => self ! heartbeat, heartbeatTiming.length, heartbeatTiming.unit)
  }

  /**
   * On receiving heartbeats, 'connect' to the delegation. If currently
   * connected then send a normal heartbeat message to check the connection,
   * otherwise send an available message to reconnect.
   *
   * On receiving any other message, process it with the respond method
   * and then send a new available message to the delegation.
   *
   * If any connection to the delegation node fails, then set to
   * disconnected and reconnect on next heartbeat.
   */
  def receive = {
    case _: Heartbeat =>
      if (connected) {
        connection {
          delegation ! heartbeat
        } otherwise {
          EventHandler.debug(this, "Lost connection")
          connected = false
        }
      } else {
        connection {
          delegation ! available
          EventHandler.debug(this, "Connected")
          connected = true
        }
      }
      nextHeartbeat()
    case message =>
      EventHandler.debug(this, "Received message: " + message)
      connection {
        respond(message)
        delegation ! available
      } otherwise {
        connected = false
      }
  }

  /**
   * The 'receive' method for delegates.
   */
  def respond: Receive

  /**
   * If this node is shutdown then signal unavailability.
   */
  override def postStop = {
    // sender must be None, otherwise we get an exception in remote
    // send because this actor has been stopped
    connection { delegation.!(Unavailable(address))(sender = None) }
  }
}


// ------------------------------------------------------------
// Delegations
// ------------------------------------------------------------

/**
 * Base delegation that uses a mutable Buffer, which is used as
 * a general purpose mutable sequence.
 */
trait BufferDelegation extends Delegation {
  val delegates = mutable.Buffer.empty[Address]

  def add(delegate: Address) = {
    if (!delegates.contains(delegate)) delegates += delegate
  }

  def remove(delegate: Address) = delegates -= delegate

  def hasNext = !delegates.isEmpty
}

/**
 * Random choice from a set of delegates.
 */
trait RandomDelegation extends BufferDelegation {
  import scala.util.Random.{nextInt => random}

  def next() = delegates(random(delegates.size))
}

/**
 * Round robin select from a sequence of delegates.
 */
trait RoundRobinDelegation extends BufferDelegation {
  private var index = 0

  def next() = {
    index = (index + 1) % delegates.size
    delegates(index)
  }
}

/**
 * A queue of next available delegates. Each delegate is removed
 * from the queue on delegation and gets added again with an
 * Available message after completing the work.
 */
trait NextAvailableDelegation extends BufferDelegation {
  def next() = delegates.remove(0)
}


// ------------------------------------------------------------
// Connections
// ------------------------------------------------------------

/**
 * A wrapper for responding to possible connection failures if
 * other nodes are not reachable.
 */
object Connection {
  import java.nio.channels.ClosedChannelException

  def connection(body: => Unit): Connection =
    try {
      body; Sent
    } catch {
      case e: ClosedChannelException => Failed
    }
}

/**
 * Interface for successful or failed connection.
 * Can be queried to see if successful or not.
 * Can have an otherwise block supplied for failures.
 */
trait Connection {
  def sent: Boolean
  def failed = !sent
  def otherwise(onError: => Unit): Unit
}

/**
 * Returned from successful connection.
 */
case object Sent extends Connection {
  def sent = true
  def otherwise(onError: => Unit) = () // do nothing
}

/**
 * Returned from failed connection.
 */
case object Failed extends Connection {
  def sent = false
  def otherwise(onError: => Unit) = onError
}
