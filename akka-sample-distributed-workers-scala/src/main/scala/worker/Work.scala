package worker

case class Work(workId: String, job: Any) extends CborSerializable

case class WorkResult(workId: String, result: Any) extends CborSerializable
