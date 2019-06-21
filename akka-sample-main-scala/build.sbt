organization := "com.typesafe.akka.samples"
name := "akka-sample-main-scala"

enablePlugins(GraalVMNativeImagePlugin)

mainClass := Some("sample.hello.Main")

// Build it with `sbt graalvm-native-image:packageBin`
graalVMNativeImageOptions := Seq(
  s"-H:ConfigurationFileDirectories=${baseDirectory.value}/graal",
  "--initialize-at-build-time",
  "--no-fallback",
)

unmanagedJars in Compile += file(sys.env("GRAAL_HOME") + "/jre/lib/svm/builder/svm.jar")

scalaVersion := "2.12.8"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.23",
  "com.github.vmencik" %% "graal-akka-actor" % "0.1.2-SNAPSHOT" % "provided",
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
