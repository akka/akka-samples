package functionalmodeling.chapter08_persistence
package cqrs
package lib

import scalaz._
import Scalaz._
import Common._
import spray.json._

import scala.collection.concurrent.TrieMap

trait EventStore[K] {
  /**
    * gets the list of events for an aggregate key 'key'
    */
  def get(key: K): List[Event[_]]

  def put(key: K, event: Event[_]): Error \/ Event[_]

  def events(key: K): Error \/ List[Event[_]]

  def allEvents: Error \/ List[Event[_]]

}

object InMemoryEventStore {
  def apply[K] = new EventStore[K] {
    val eventLog = TrieMap[K, List[Event[_]]]()

    override def get(key: K) = eventLog.getOrElse(key, List.empty[Event[_]])

    override def put(key: K, event: Event[_]) = {
      val currentList = eventLog.getOrElse(key, List.empty[Event[_]])
      eventLog += (key -> (event :: currentList))
      event.right
    }

    override def events(key: K) = {
      eventLog.get(key) match {
        case Some(es) => es.right
        case None => s"Aggregate $key does not exist".left
      }
    }

    override def allEvents = eventLog.values.toList.flatten.right
  }
}

trait InMemoryJSONEventStore {
  implicit val eventJsonFormat: RootJsonFormat[Event[_]]

  def apply[K] = new EventStore[K] {
    val eventLog = TrieMap[K, List[String]]()

    override def get(key: K) =
      eventLog.get(key).map(ls => ls.map(_.parseJson.convertTo[Event[_]])).getOrElse(Nil)

    def put(key: K, event: Event[_]): Error \/ Event[_] = {
      val currentList = eventLog.getOrElse(key, Nil)
      eventLog += (key -> (eventJsonFormat.write(event).toString :: currentList))
      event.right
    }

    override def events(key: K) = {
      eventLog.get(key) match {
        case Some(es) => es.map(_.parseJson.convertTo[Event[_]]).right
        case None => s"Aggregate $key does not exist".left
      }
    }

    override def allEvents = eventLog.values.toList.flatten.map(_.parseJson.convertTo[Event[_]]).right
  }
}