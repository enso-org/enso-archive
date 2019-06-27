package org.enso.projectmanager.services

import java.io.File
import java.util.UUID

import org.enso.pkg.Package
import org.enso.projectmanager.model.Local
import org.enso.projectmanager.model.Project
import org.enso.projectmanager.model.ProjectType
import org.enso.projectmanager.model.ProjectsRepository
import org.enso.projectmanager.model.Temporary
import org.enso.projectmanager.model.Tutorial

import scala.collection.immutable.HashMap

case class StorageManager(
  localProjectsPath: File,
  tmpProjectsPath: File,
  tutorialsPath: File) {

  localProjectsPath.mkdirs()
  tmpProjectsPath.mkdirs()
  tutorialsPath.mkdirs()

  def persist(project: Project, newName: Option[String]): Project = {
    val pkg     = project.pkg
    val renamed = newName.map(pkg.rename).getOrElse(pkg)
    val root    = assignRootForName(localProjectsPath, renamed.name)
    val moved   = renamed.move(root)
    Project(Local, moved)
  }

  def assignRootForName(
    rootDir: File,
    name: String,
    idx: Option[Int] = None
  ): File = {
    val idxSuffix = idx.map(idx => s".$idx").getOrElse("")
    val nameToTry = name + idxSuffix
    val rootToTry = new File(rootDir, nameToTry)

    if (rootToTry.exists()) {
      assignRootForName(rootDir, name, Some(idx.map(_ + 1).getOrElse(0)))
    }
    else rootToTry
  }

  def readLocalProjects: ProjectsRepository =
    listProjectsInDirectory(Local, localProjectsPath)

  def readTutorials: ProjectsRepository =
    listProjectsInDirectory(Tutorial, tutorialsPath)

  def listProjectsInDirectory(
    kind: ProjectType,
    dir: File
  ): ProjectsRepository = {
    val candidates = dir.listFiles(_.isDirectory).toList
    val projects   = candidates.map(Package.getOrCreate).map(Project(kind, _))
    ProjectsRepository(HashMap(projects.map(UUID.randomUUID() -> _): _*))
  }

  def createTemporary(name: String): Project = {
    val root = assignRootForName(tmpProjectsPath, name)
    val pkg  = Package.create(root, name)
    Project(Temporary, pkg)
  }
}
