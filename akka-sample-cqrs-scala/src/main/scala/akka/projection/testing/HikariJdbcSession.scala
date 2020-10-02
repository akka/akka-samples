package akka.projection.testing

import java.sql.Connection

import akka.japi.function
import akka.projection.jdbc.JdbcSession
import javax.sql.DataSource



class HikariJdbcSession(source: DataSource) extends JdbcSession {

  private val connection = source.getConnection

  override def withConnection[Result](func: function.Function[Connection, Result]): Result =
    func(connection)

  override def commit(): Unit = connection.commit()

  override def rollback(): Unit = connection.rollback()

  override def close(): Unit = connection.close()
}
