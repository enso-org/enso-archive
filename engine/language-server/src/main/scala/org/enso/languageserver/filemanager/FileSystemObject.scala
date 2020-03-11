package org.enso.languageserver.filemanager

import java.nio
import java.nio.file.Paths

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

/**
  * A representation of filesystem object.
  */
sealed trait FileSystemObject

object FileSystemObject {

  /**
    * Represents a directory.
    *
    * @param name a name of the directory
    * @param path a path to the directory
    */
  case class Directory(name: String, path: Path) extends FileSystemObject

  /**
    * Represents a directory which contents have been truncated.
    *
    * @param name a name of the directory
    * @param path a path to the directory
    */
  case class DirectoryTruncated(name: String, path: Path)
      extends FileSystemObject

  /**
    * Represents a symbolic link that creates a loop.
    *
    * @param name a name of the symlink
    * @param path a path to the symlink
    */
  case class SymlinkLoop(name: String, path: Path) extends FileSystemObject

  /**
    * Represents a file.
    *
    * @param name a name of the file
    * @param path a path to the file
    */
  case class File(name: String, path: Path) extends FileSystemObject

  /**
    * Represents unrecognized object. Example is a broken symlink.
    */
  case class Other(name: String, path: Path) extends FileSystemObject

  private object CodecField {

    val Type = "type"

    val Name = "name"

    val Path = "path"
  }

  private object CodecType {

    val File = "File"

    val Directory = "Directory"

    val DirectoryTruncated = "DirectoryTruncated"

    val SymlinkLoop = "SymlinkLoop"

    val Other = "Other"
  }

  implicit val decoder: Decoder[FileSystemObject] =
    Decoder.instance { cursor =>
      cursor.downField(CodecField.Type).as[String].flatMap {
        case CodecType.File =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.Path).as[Path]
          } yield File(name, path)

        case CodecType.Directory =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.Path).as[Path]
          } yield Directory(name, path)

        case CodecType.DirectoryTruncated =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.Path).as[Path]
          } yield DirectoryTruncated(name, path)

        case CodecType.SymlinkLoop =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.Path).as[Path]
          } yield SymlinkLoop(name, path)

        case CodecType.Other =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.Path).as[Path]
          } yield Other(name, path)
      }
    }

  implicit val encoder: Encoder[FileSystemObject] =
    Encoder.instance[FileSystemObject] {
      case Directory(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.Directory.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )

      case DirectoryTruncated(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.DirectoryTruncated.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )

      case SymlinkLoop(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.SymlinkLoop.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )

      case File(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.File.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )

      case Other(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.Other.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )
    }

  implicit val ordering: Ordering[FileSystemObject] =
    Ordering.by(FileSystemObject.Order.by)

  object Order {
    def by(obj: FileSystemObject): nio.file.Path =
      obj match {
        case Directory(name, path) =>
          Paths.get("", path.segments :+ name :+ "Directory": _*)
        case DirectoryTruncated(name, path) =>
          Paths.get("", path.segments :+ name :+ "DirectoryTruncated": _*)
        case SymlinkLoop(name, path) =>
          Paths.get("", path.segments :+ name :+ "SymlinkLoop": _*)
        case File(name, path) =>
          Paths.get("", path.segments :+ name :+ "File": _*)
        case Other(name, path) =>
          Paths.get("", path.segments :+ name :+ "Other": _*)
      }
  }
}
