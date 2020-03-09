package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.Paths
import java.util.UUID

/**
  * A representation of a path.
  */
sealed trait FilePath

/**
  * A representation of a path relative to a specified content root.
  *
  * @param rootId a content root id that the path is relative to
  * @param segments path segments
  */
case class Path(rootId: UUID, segments: List[String]) {

  def toFile(rootPath: File): File =
    segments.foldLeft(rootPath) {
      case (parent, child) => new File(parent, child)
    }

  def toFile(rootPath: File, fileName: String): File = {
    val parentDir = toFile(rootPath)
    new File(parentDir, fileName)
  }

  def toFile: File =
    Paths.get("", segments: _*).toFile
}

object Path {

  def apply(rootId: UUID, path: java.nio.file.Path): Path = {
    val b = List.newBuilder[String]
    path.forEach(p => b += p.toString())
    new Path(rootId, b.result().filter(_.nonEmpty))
  }
}

/**
  * A representation of an absolute path.
  *
  * @param segments path segments
  */
case class SystemPath(segments: List[String])
