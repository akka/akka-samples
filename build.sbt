lazy val `akka-samples` = project
  .in(file("."))
  .aggregate(
    `akka-sample-camel-java`,
    `akka-sample-camel-scala`,
    `akka-sample-main-java`,
    `akka-sample-main-scala`
  )

lazy val `akka-sample-camel-java` = project
lazy val `akka-sample-camel-scala` = project

lazy val `akka-sample-main-java` = project
lazy val `akka-sample-main-scala` = project
