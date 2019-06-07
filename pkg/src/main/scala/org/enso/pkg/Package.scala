package org.enso.pkg

import java.io.File
import java.io.PrintWriter

import scala.io.Source
import scala.util.Try

object CouldNotCreateDirectory extends Exception

case class Package(root: File, config: Config) {
  val sourceDirName = "src"
  val mainFileName = "Main.luna"
  val sourceDir = new File(root, sourceDirName)

  def save(): Unit = {
    if (!root.exists()) createDirectories()
    if (!sourceDir.exists()) createSourceDir()
    saveConfig()
  }

  def createDirectories() {
    val created = Try(root.mkdirs).getOrElse(false)
    if (!created) throw CouldNotCreateDirectory
    createSourceDir()
  }

  def createSourceDir(): Unit = {
    if (!Try(sourceDir.mkdir).getOrElse(false)) throw CouldNotCreateDirectory
    val lunaCodeSrc = Source.fromResource(mainFileName)
    val writer = new PrintWriter(new File(sourceDir, mainFileName))
    writer.write(lunaCodeSrc.mkString)
    writer.close()
    lunaCodeSrc.close()
  }

  def saveConfig(): Unit = {
    val writer = new PrintWriter(new File(root, Package.configFileName))
    Try(writer.write(config.toYaml))
    writer.close()
  }
}

object Package {
  val configFileName = "package.yaml"

  def create(root: File, config: Config): Package = {
    val pkg = Package(root, config)
    pkg.save()
    pkg
  }

  def create(root: File, name: String): Package = {
    val config = Config(
      author = "",
      maintainer = "",
      name = name,
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

  def generateName(file: File): String = {
    val dirname = file.getName
    val startingWithLetter =
      if (!dirname(0).isLetter) "Project" ++ dirname else dirname
    val startingWithUppercase = startingWithLetter.capitalize
    val onlyAlphanumeric = startingWithUppercase.filter(_.isLetterOrDigit)
    onlyAlphanumeric
  }
}
