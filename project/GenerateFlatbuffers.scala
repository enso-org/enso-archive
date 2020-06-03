import java.io.IOException

import sbt.Keys._
import sbt._

import scala.sys.process._
import sbt.Tracked

object GenerateFlatbuffers {

  val flatcVersion     = settingKey[String]("flatc version used in the project")
  private val flatcCmd = "flatc"

  lazy val task = Def.task {
    verifyFlatcVersion(flatcVersion.value)

    val root = baseDirectory.value
    val schemas =
      (file(s"$root/src/main/schema") ** "*.fbs").get

    val out = (sourceManaged in Compile).value
    // Note [flatc Generated Sources Detection]
    val generatedFilenames = schemas flatMap { schema =>
        val cmdGenerate = s"$flatcCmd --java -o ${out.getAbsolutePath} $schema"
        val cmdMakeRules =
          s"$flatcCmd -M --java -o ${out.getAbsolutePath} $schema"

        val exitCode = cmdGenerate.!
        if (exitCode != 0) {
          println(
            f"Generating $schema with command `$cmdGenerate` has failed with exit code $exitCode"
          )

          throw new RuntimeException(
            f"Flatbuffer code generation failed for $schema"
          )
        }

        val makeRules = cmdMakeRules.!!
        extractGeneratedFilenamesFromMakefile(makeRules)
      }

    val uniqueFilenames      = generatedFilenames.distinct
    val uniqueGeneratedFiles = uniqueFilenames.map(file)

    if (uniqueGeneratedFiles.nonEmpty) {
      val projectName = name.value
      println(
        f"*** Flatbuffers code generation generated ${uniqueGeneratedFiles.length} files in project $projectName"
      )
    }

    uniqueGeneratedFiles
  }

  private def verifyFlatcVersion(expectedVersion: String): Unit = {
    val cmd = f"$flatcCmd --version"
    val versionStr =
      try {
        cmd.!!.trim
      } catch {
        case ex @ (_: RuntimeException | _: IOException) =>
          println("flatc version check failed. Make sure flatc is in your PATH")
          throw new RuntimeException("Could not check flatc version", ex)
      }

    val expectedVersionStr = s"flatc version $expectedVersion"
    if (expectedVersionStr != versionStr) {
      println("flatc version mismatch.")
      println(
        s"$expectedVersionStr is expected, but it seems $versionStr is installed"
      )
      throw new RuntimeException("flatc version mismatch")
    }
  }

  /** Parses the Make rules returned by flatc to get a list of affected files.
    *
    * @param makeRules a string representing the rules returned by flatc
    * @return a sequence of filenames that are mentioned in the provided rules
    */
  private def extractGeneratedFilenamesFromMakefile(
    makeRules: String
  ): Seq[String] = {
    val cleaned            = makeRules.replaceAllLiterally("\\", "");
    val Array(javaPart, _) = cleaned.split(':')

    val filenames = javaPart.split('\n').map(_.trim).filter(_.length > 0)
    filenames
  }

  private def gatherGeneratedSources(schemas: Seq[File]): Seq[File] = {
    Nil // FIXME
  }
}

/* Note [flatc Generated Sources Detection]
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * The flatc compiler can print Make rules for files it generates (using the -M option).
 * We use this feature to easily get a list of files affected by each schema definition.
 * The printed rules have the following format:
 * ```
 * file1.java \
 * file2.java \
 *   ...
 * fileN.java: \
 * fileA.fbs \
 * fileB.fbs \
 *   ...
 * ```
 * Each flatc run lists all affected files, for files used in multiple schemas that means they may appear more than
 * once. Because of that we make sure to remove any potential duplicates before returning the list of generated sources.
 */
