package worker

case class Work(workId: String, job: Int) extends CborSerializable

case class WorkResult(workId: String, result: Any) extends CborSerializable
