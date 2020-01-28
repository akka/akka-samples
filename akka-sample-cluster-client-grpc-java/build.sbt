val akkaVersion = "2.6.3"

lazy val `akka-sample-cluster-client-grpc-java` = project
  .in(file("."))
  .enablePlugins(JavaAgent)
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    organization := "com.typesafe.akka",
    scalaVersion := "2.12.8",
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-parameters"),
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
    // javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime",
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
        "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
        "junit" % "junit" % "4.12" % Test,
        "com.novocode" % "junit-interface" % "0.11" % Test))
