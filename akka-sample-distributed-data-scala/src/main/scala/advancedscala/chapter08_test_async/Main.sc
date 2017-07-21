import cats.Id

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.language.higherKinds
import cats.syntax.traverse._
import cats.instances.list._
import cats.instances.future._
import cats.Applicative
import cats.syntax.functor._

trait UptimeClient[F[_]] {
  def getUptime(hostname: String): F[Int]
}

//class UptimeService[F[_]](client: UptimeClient[F])(implicit app: Applicative[F]) {
class UptimeService[F[_] : Applicative](client: UptimeClient[F]) {
  def getTotalUptime(hostnames: List[String]): F[Int] = {
    hostnames.traverse(client.getUptime).map(_.sum)
  }
}

trait RealUptimeClient extends UptimeClient[Future] {
  override def getUptime(hostname: String): Future[Int]
}

trait TestUptimeClient extends UptimeClient[Id] {
  override def getUptime(host: String): Int
}

class TestUptimeClientUsingMap(hosts: Map[String, Int]) extends TestUptimeClient {
  override def getUptime(host: String): Int = hosts.getOrElse(host, 0)
}

def testTotalTime() = {
  val hosts = Map("host1" -> 10, "host2" -> 6)
  val client = new TestUptimeClientUsingMap(hosts)
  val service = new UptimeService(client)

  val actual = service.getTotalUptime(hosts.keys.toList)
  val expected = hosts.values.sum

  assert(actual == expected)
}

testTotalTime()
