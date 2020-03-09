package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID

/**
  * A representation of a path.
  */
sealed trait Segments {
  def segments: List[String]
}

/**
  * A representation of a path relative to a specified content root.
  *
  * @param rootId a content root id that the path is relative to
  * @param segments path segments
  */
case class RelativePath(rootId: UUID, segments: List[String]) extends Segments {

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

object RelativePath {

  def apply(rootId: UUID, path: Path): RelativePath = {
    val b = List.newBuilder[String]
    path.forEach(p => b += p.toString())
    new RelativePath(rootId, b.result().filter(_.nonEmpty))
  }
}

/**
  * A representation of an absolute path.
  *
  * @param segments path segments
  */
case class AbsolutePath(segments: List[String]) extends Segments
