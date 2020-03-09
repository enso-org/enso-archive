package org.enso.languageserver.filemanager

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
  case class Directory(name: String, path: RelativePath)
      extends FileSystemObject

  /**
    * Represents a file.
    *
    * @param name a name of the file
    * @param path a path to the file
    */
  case class File(name: String, path: RelativePath) extends FileSystemObject

  /**
    * Represents a symbolic link.
    *
    * @param source a link path
    * @param target a destination of the link
    */
  case class Symlink(source: RelativePath, target: SystemPath)
      extends FileSystemObject

  /**
    * Represents unrecognized object.
    */
  case object Other extends FileSystemObject

  private object CodecField {

    val Type = "type"

    val Name = "name"

    val RelativePath = "path"

    val Source = "source"

    val Target = "target"
  }

  private object CodecType {

    val File = "File"

    val Directory = "Directory"

    val Symlink = "Symlink"

    val Other = "Other"
  }

  implicit val decoder: Decoder[FileSystemObject] =
    Decoder.instance { cursor =>
      cursor.downField(CodecField.Type).as[String].flatMap {
        case CodecType.File =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.RelativePath).as[RelativePath]
          } yield File(name, path)

        case CodecType.Directory =>
          for {
            name <- cursor.downField(CodecField.Name).as[String]
            path <- cursor.downField(CodecField.RelativePath).as[RelativePath]
          } yield Directory(name, path)

        case CodecType.Symlink =>
          for {
            source <- cursor.downField(CodecField.Source).as[RelativePath]
            target <- cursor.downField(CodecField.Target).as[SystemPath]
          } yield Symlink(source, target)

        case CodecType.Other =>
          Right(Other)
      }
    }

  implicit val encoder: Encoder[FileSystemObject] =
    Encoder.instance[FileSystemObject] {
      case Directory(name, path) =>
        Json.obj(
          CodecField.Type         -> CodecType.Directory.asJson,
          CodecField.Name         -> name.asJson,
          CodecField.RelativePath -> path.asJson
        )

      case File(name, path) =>
        Json.obj(
          CodecField.Type         -> CodecType.File.asJson,
          CodecField.Name         -> name.asJson,
          CodecField.RelativePath -> path.asJson
        )

      case Symlink(source, target) =>
        Json.obj(
          CodecField.Type   -> CodecType.Symlink.asJson,
          CodecField.Source -> source.asJson,
          CodecField.Target -> target.asJson
        )

      case Other =>
        Json.obj(
          CodecField.Type -> CodecType.Other.asJson
        )
    }

  implicit val ordering: Ordering[FileSystemObject] =
    Ordering.by {
      case Directory(name, path) => new java.io.File(path.toFile, name)
      case File(name, path)      => new java.io.File(path.toFile, name)
      case Symlink(source, _)    => source.toFile
      case Other                 => new java.io.File("/")
    }
}
