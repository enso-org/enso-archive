import sbt.Keys._
import sbt._
import scala.sys.process._

object GenFbs extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger  = allRequirements

  object autoImport {
    val genFbs =
      taskKey[Unit]("Run flatc compiler to generate Java classes for schema")
  }
  import autoImport._

  override lazy val projectSettings = Seq(
    genFbs := {
      val root = baseDirectory.value
      val schemas =
        (file(s"$root/src/main/schema") ** "*.fbs").get
          .map(_.getAbsolutePath)
          .toList

      schemas foreach { schema =>
        println(s"*** Generating Java classes for schema: $schema")
        val result = s"flatc --java -o $root/src/main/java $schema".!!
        println(
          s"*** Generated Java classes from FlatBuffer schema $schema. Results: '$result'"
        )
      }
    }
  )
}
