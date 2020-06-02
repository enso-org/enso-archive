import sbt.Keys._
import sbt._

import scala.sys.process._

object GenerateFlatbuffers {

  lazy val task = Def.task {
    val flatcCmd = "flatc"
    val root     = baseDirectory.value
    val schemas =
      (file(s"$root/src/main/schema") ** "*.fbs").get
        .map(_.getAbsolutePath)
        .toList

    /** Parses the Make rules returned by flatc to get a list of affected files.
      *
      * @param makeRules a string representing the rules returned by flatc
      * @return a sequence of filenames that are mentioned in the provided rules
      */
    def extractGeneratedFilenamesFromMakefile(
      makeRules: String
    ): Seq[String] = {
      val cleaned            = makeRules.replaceAllLiterally("\\", "");
      val Array(javaPart, _) = cleaned.split(':')

      val filenames = javaPart.split('\n').map(_.trim).filter(_.length > 0)
      filenames
    }

    val out = (sourceManaged in Compile).value
    // Note [flatc Generated Sources Detection]
    val generatedFilenames = schemas flatMap { schema =>
        val cmd = s"$flatcCmd -M --java -o ${out.getAbsolutePath} $schema"
        try {
          val makeRules = cmd.!!
          extractGeneratedFilenamesFromMakefile(makeRules)
        } catch {
          case ex: RuntimeException =>
            val restarted = cmd.run() // Note [flatc Error Reporting]
            val exitValue = restarted.exitValue()
            println(
              f"Generating $schema with command `$cmd` has failed with exit code $exitValue"
            )
            throw ex
        }
      }

    val uniqueFilenames      = generatedFilenames.distinct
    val uniqueGeneratedFiles = uniqueFilenames.map(file)

    if (uniqueGeneratedFiles.nonEmpty) {
      val projectName = name.value
      println(
        f"*** Flatbuffers code generation generated ${uniqueGeneratedFiles.length} files for project $projectName"
      )
    }

    uniqueGeneratedFiles
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

/* Note [flatc Error Reporting]
 * As flatc reports errors to stdout and not stderr, if it fails the output is captured but not accessible (because !!
 * only returns the output if the command succeeded).
 * To avoid complex process handling logic, upon detecting an error, flatc is re-run giving it a chance to print the
 * errors.
 */
