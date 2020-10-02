package akka.projection.testing

import javax.sql.DataSource

class HikariFactory(val dataSource: DataSource) {
  def newSession(): HikariJdbcSession = {
    new HikariJdbcSession(dataSource)
  }
}
