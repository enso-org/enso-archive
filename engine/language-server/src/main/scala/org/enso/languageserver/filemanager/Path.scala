package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

/**
  * A representation of a path.
  */
sealed trait SystemPath {
  def segments: List[String]
}

object SystemPath {

  def segments(path: Path): List[String] = {
    val b = List.newBuilder[String]
    path.forEach(p => b += p.toString())
    b.result().filter(_.nonEmpty)
  }

  private object CodecField {

    val Type = "type"

    val Segments = "segments"

    val RootId = "rootId"
  }

  private object CodecType {

    val RelativePath = "Relative"

    val AbsolutePath = "Absolute"
  }

  implicit val encoder: Encoder[SystemPath] =
    Encoder.instance[SystemPath] {
      case RelativePath(rootId, segments) =>
        Json.obj(
          CodecField.Type     -> CodecType.RelativePath.asJson,
          CodecField.RootId   -> rootId.asJson,
          CodecField.Segments -> segments.asJson
        )

      case AbsolutePath(segments) =>
        Json.obj(
          CodecField.Type     -> CodecType.AbsolutePath.asJson,
          CodecField.Segments -> segments.asJson
        )
    }

  implicit val decoder: Decoder[SystemPath] =
    Decoder.instance { cursor =>
      cursor.downField(CodecField.Type).as[String].flatMap {
        case CodecType.RelativePath =>
          for {
            rootId   <- cursor.downField(CodecField.RootId).as[UUID]
            segments <- cursor.downField(CodecField.Segments).as[List[String]]
          } yield RelativePath(rootId, segments)

        case CodecType.AbsolutePath =>
          for {
            segments <- cursor.downField(CodecField.Segments).as[List[String]]
          } yield AbsolutePath(segments)
      }
    }
}

/**
  * A representation of a path relative to a specified content root.
  *
  * @param rootId a content root id that the path is relative to
  * @param segments path segments
  */
case class RelativePath(rootId: UUID, segments: List[String])
    extends SystemPath {

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

  def apply(rootId: UUID, path: Path): RelativePath =
    new RelativePath(rootId, SystemPath.segments(path))
}

/**
  * A representation of an absolute path.
  *
  * @param segments path segments
  */
case class AbsolutePath(segments: List[String]) extends SystemPath

object AbsolutePath {

  def apply(path: Path): AbsolutePath =
    AbsolutePath(SystemPath.segments(path))
}
