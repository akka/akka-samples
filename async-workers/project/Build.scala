package sample.async

import sbt._
import sbt.Keys._
import akka.sbt.AkkaKernelPlugin

object AsyncWorkersBuild extends Build {
  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "sample",
    version      := "0.1",
    scalaVersion := "2.9.1"
  )

  lazy val async = Project(
    id = "async",
    base = file("."),
    settings = buildSettings,
    aggregate = Seq(delegation, master, worker)
  )

  lazy val delegation = Project(
    id = "delegation",
    base = file("delegation"),
    settings = defaultSettings ++ Seq(
      libraryDependencies += "se.scalablesolutions.akka" % "akka-actor" % "1.1.3"
    )
  )

  lazy val master = Project(
    id = "master",
    base = file("master"),
    dependencies = Seq(delegation),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies += "se.scalablesolutions.akka" % "akka-kernel" % "1.1.3"
    )
  )

  lazy val worker = Project(
    id = "worker",
    base = file("worker"),
    dependencies = Seq(delegation),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies += "se.scalablesolutions.akka" % "akka-kernel" % "1.1.3"
    )
  )

  lazy val defaultSettings = buildSettings ++ (
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
  )
}
