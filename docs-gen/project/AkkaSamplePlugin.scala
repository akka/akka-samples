import sbt._
import Keys._
import com.lightbend.paradox.sbt.ParadoxPlugin

object AkkaSamplePlugin extends sbt.AutoPlugin {
  override def requires = ParadoxPlugin
  override def trigger = allRequirements
  object autoImport {
    val baseProject = settingKey[String]("")
    val templateName = settingKey[String]("")
    val bodyPrefix = settingKey[String]("")
  }
  import autoImport._
  import ParadoxPlugin.autoImport._
  override def projectSettings: Seq[Setting[_]] = Seq(
    crossPaths := false,
    // Copy README.md file
    sourceDirectory in (Compile, paradox) := {
      val outDir = (managedSourceDirectories in Compile).value.head / "paradox"
      val outFile = outDir / "index.md"
      val inDir = baseDirectory.value / ".." / ".." / baseProject.value
      val inFile = inDir / "README.md"
      IO.write(outFile, bodyPrefix.value + IO.read(inFile))
      if ((inDir / "tutorial").exists) {
        IO.copyDirectory(inDir / "tutorial", outDir / "tutorial")
      }
      outDir
    },
    paradoxProperties += ("download_url" -> s"https://example.lightbend.com/v1/download/${templateName.value}"),
    bodyPrefix := s"""${name.value}
                     |=======================
                     |
                     |""".stripMargin,
    templateName := baseProject.value.replaceAll("-sample-", "-samples-")
  )
}
