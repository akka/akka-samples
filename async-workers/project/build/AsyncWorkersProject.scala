import sbt._

class AsyncWorkersProject(info: ProjectInfo) extends ParentProject(info) {

  lazy val delegation = project("delegation", "Async Delegation", new DelegationProject(_))
  lazy val master = project("master", "Async Master", new MasterProject(_), delegation)
  lazy val worker = project("worker", "Async Worker", new WorkerProject(_), delegation)

  class DelegationProject(info: ProjectInfo) extends DefaultProject(info) with AkkaProject {
    override def disableCrossPaths = true
  }

  class MasterProject(info: ProjectInfo) extends DefaultProject(info) with AkkaKernelProject {
    override def disableCrossPaths = true
  }

  class WorkerProject(info: ProjectInfo) extends DefaultProject(info) with AkkaKernelProject {
    override def disableCrossPaths = true
  }
}
