package org.enso.pkg

import java.io.File
import java.io.PrintWriter
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._


import org.apache.commons.io.FileUtils

import scala.io.Source
import scala.util.Try

object CouldNotCreateDirectory extends Exception

case class SourceFile(qualifiedName: String, file: File)

case class Package(root: File, config: Config) {

  val sourceDir = new File(root, Package.sourceDirName)
  val configFile = new File(root, Package.configFileName)
  val thumbFile = new File(root, Package.thumbFileName)

  def save(): Unit = {
    if (!root.exists) createDirectories()
    if (!sourceDir.exists) createSourceDir()
    saveConfig()
  }

  def createDirectories() {
    val created = Try(root.mkdirs).getOrElse(false)
    if (!created) throw CouldNotCreateDirectory
    createSourceDir()
  }

  def rename(newName: String): Package = {
    val newPkg = copy(config = config.copy(name = newName))
    newPkg.save()
    newPkg
  }

  def remove(): Unit = {
    FileUtils.deleteDirectory(root)
  }

  def move(newRoot: File): Package = {
    val newPkg = copyPackage(newRoot)
    remove()
    newPkg
  }

  def copyPackage(newRoot: File): Package = {
    FileUtils.copyDirectory(root, newRoot)
    copy(root = newRoot)
  }

  def createSourceDir(): Unit = {
    if (!Try(sourceDir.mkdir).getOrElse(false)) throw CouldNotCreateDirectory
    val mainCodeSrc = Source.fromResource(Package.mainFileName)
    val writer = new PrintWriter(new File(sourceDir, Package.mainFileName))
    writer.write(mainCodeSrc.mkString)
    writer.close()
    mainCodeSrc.close()
  }

  def saveConfig(): Unit = {
    val writer = new PrintWriter(configFile)
    Try(writer.write(config.toYaml))
    writer.close()
  }

  def mainFile(): File = {
    new File(sourceDir, Package.mainFileName)
  }

  def hasThumb: Boolean = thumbFile.exists

  def name: String = config.name

  def listSources: List[SourceFile] = {
    val sourcesPath = sourceDir.toPath
    val sources = Files.walk(sourcesPath).filter(Files.isRegularFile(_)).iterator.asScala.toList
    sources.map { path =>
      val segments = sourcesPath.relativize(path).iterator().asScala.toList
      val dirSegments = segments.take(segments.length - 1).map(_.toString)
      val fileNameWithoutExtension = path.getFileName.toString.takeWhile(_ != '.')
      val qualName = (name :: (dirSegments :+ fileNameWithoutExtension)).mkString(Package.qualifiedNameSeparator)
      SourceFile(qualName, path.toFile)
    }
  }
}

object Package {
  val configFileName = "package.yaml"
  val sourceDirName = "src"
  val mainFileName = "Main.enso"
  val thumbFileName = "thumb.png"
  val qualifiedNameSeparator = "."

  def create(root: File, config: Config): Package = {
    val pkg = Package(root, config)
    pkg.save()
    pkg
  }

  def create(root: File, name: String): Package = {
    val config = Config(
      author = "",
      maintainer = "",
      name = normalizeName(name),
      version = "",
      license = ""
    )
    create(root, config)
  }

  def fromDirectory(root: File): Option[Package] = {
    if (!root.exists()) return None
    val configFile = new File(root, configFileName)
    val source = Try(Source.fromFile(configFile))
    val result = source.map(_.mkString).toOption.flatMap(Config.fromYaml)
    source.foreach(_.close())
    result.map(Package(root, _))
  }

  def getOrCreate(root: File): Package = {
    val existing = fromDirectory(root)
    existing.getOrElse(create(root, generateName(root)))
  }

  def normalizeName(name: String): String = {
    val startingWithLetter =
      if (name.length == 0 || !name(0).isLetter) "Project" ++ name else name
    val startingWithUppercase = startingWithLetter.capitalize
    val onlyAlphanumeric = startingWithUppercase.filter(_.isLetterOrDigit)
    onlyAlphanumeric
  }

  def generateName(file: File): String = {
    val dirname = file.getName
    normalizeName(dirname)
  }
}
