package functionalmodeling.chapter08_persistence
package cqrs
package lib

import Common._
import org.joda.time.DateTime

import scalaz._
import Scalaz._
import scalaz.concurrent.Task

object Common {
  type AggregateId = String
  type Error = String
}

trait Event[A] {
  def at: DateTime
}

trait Aggregate {
  def id: AggregateId
}

trait Snapshot[A <: Aggregate] {
  def updateState(e: Event[_], initial: Map[String, A]): Map[String, A]

  def snapshot(es: List[Event[_]]): String \/ Map[String, A] =
    es.reverse.foldLeft(Map.empty[String, A]) { (a, e) =>
      updateState(e, a)
    }.right
}

trait Commands[A] {
  type Command[A] = Free[Event, A]
}

trait RepositoryBackedInterpreter {
  def step: Event ~> Task

  def apply[A](action: Free[Event, A]): Task[A] = action.foldMap(step)
}