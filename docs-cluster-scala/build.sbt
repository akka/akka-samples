val baseProject  = "akka-sample-cluster-scala"
val templateName = "akka-samples-cluster-scala"
val prefix = """Akka Cluster with Scala
               |=======================
               |
               |""".stripMargin

lazy val docs = (project in file("."))
  .enablePlugins(ParadoxPlugin)
  .settings(
    name := "docs-cluster-scala",
    crossPaths := false,
    sourceDirectory in (Compile, paradox) := {
      val outDir = (managedSourceDirectories in Compile).value.head / "paradox"
      val outFile = outDir / "index.md"
      val inFile = baseDirectory.value / ".." / baseProject / "README.md"
      IO.write(outFile, prefix + IO.read(inFile))
      outDir
    },
    paradoxProperties += ("download_url" -> s"https://example.lightbend.com/v1/download/$templateName")
  )

// Uses the out of the box generic theme.
paradoxTheme := Some(builtinParadoxTheme("generic"))
