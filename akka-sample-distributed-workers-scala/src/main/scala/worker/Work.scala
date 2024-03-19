/*
 * Copyright (C) 2009-2024 Lightbend Inc. <https://www.lightbend.com>
 */
package worker

case class Work(workId: String, job: Int) extends CborSerializable

case class WorkResult(workId: String, result: Any) extends CborSerializable
