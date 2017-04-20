lazy val `akka-sample-cluster-scala` = (project in file("akka-sample-cluster-scala"))
  .enablePlugins(ParadoxPlugin)
  .settings(
    name := "Akka Cluster with Scala",
    crossPaths := false
  )
