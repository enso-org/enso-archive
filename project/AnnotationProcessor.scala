import sbt.AutoPlugin
import sbt.Def
import sbt._
import sbt.Keys._
import java.io.File

import sys.process._

object AnnotationProcessorPlugin extends AutoPlugin {

  object autoImport {
    lazy val processAnnotations = taskKey[Unit]("Process Annotations")
    lazy val annotationProcessors = settingKey[Seq[String]](
      "Annotation processor for use with processAnnotations task"
    )
  }

  import autoImport._

  lazy val baseSettings: Seq[Def.Setting[_]] =
    Seq(
      processAnnotations := AnnotationProcessor(
        fullClasspath.in(processAnnotations).value.files,
        classDirectory.in(processAnnotations).value,
        sourceManaged.in(processAnnotations).value,
        annotationProcessors.value
      ),
      annotationProcessors := Seq()
    )

  override lazy val projectSettings = inConfig(Compile)(baseSettings)

}

object AnnotationProcessor {

  def recursiveGetClasses(root: File, pkgName: String = ""): Seq[String] = {
    val subdirs = root.listFiles(_.isDirectory).toSeq
    val classes = root.listFiles(_.getName.endsWith(".class")).toSeq
    val recur =
      subdirs.flatMap { dir =>
        val dirName = dir.getName
        val newPkgName =
          if (pkgName.isEmpty) dirName else s"$pkgName.$dirName"
        recursiveGetClasses(dir, newPkgName)
      }
    val classNames = classes.map { classFile =>
      val name      = classFile.getName
      val className = name.split("\\.")
      s"$pkgName.${className.head}"
    }
    classNames ++ recur
  }

  def apply(
    classPath: Seq[File],
    classDir: File,
    managedSourceDir: File,
    processors: Seq[String]
  ): Unit = {
    val classPathArg      = classPath.mkString(":")
    val destinationDirArg = classDir.getAbsolutePath
    if (classDir.exists()) {
      val classesToProcess =
        recursiveGetClasses(classDir, "").mkString(" ")
      val processorArg = processors.mkString(",")
      managedSourceDir.mkdirs()
      val command = Seq(
        "javac",
        s"-cp $classPathArg",
        s"-processor $processorArg",
        "-XprintRounds",
        s"-d $destinationDirArg",
        s"-s $managedSourceDir",
        classesToProcess
      ).mkString(" ")
      command !
    }
  }
}
