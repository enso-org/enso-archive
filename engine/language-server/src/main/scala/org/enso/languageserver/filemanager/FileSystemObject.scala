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
  case class Directory(name: String, path: Path) extends FileSystemObject

  /**
    * Represents a file.
    *
    * @param name a name of the file
    * @param path a path to the file
    */
  case class File(name: String, path: Path) extends FileSystemObject

  /**
    * Represents a symbolic link.
    *
    * @param source a link path
    * @param target a destination of the link
    */
  case class Symlink(source: Path, target: Path) extends FileSystemObject

  /**
    * Represents unrecognized object.
    */
  case object Other extends FileSystemObject

  private object CodecField {

    val Type = "type"

    val Name = "name"

    val Path = "path"

    val Source = "source"

    val Target = "target"
  }

  private object CodecType {

    val File = "File"

    val Directory = "Directory"

    val Symlink = "Symlink"

    val Other = "Other"
  }

  implicit val fsoDecoder: Decoder[FileSystemObject] =
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

        case CodecType.Symlink =>
          for {
            source <- cursor.downField(CodecField.Source).as[Path]
            target <- cursor.downField(CodecField.Target).as[Path]
          } yield Symlink(source, target)

        case CodecType.Other =>
          Right(Other)
      }
    }

  implicit val fsoEncoder: Encoder[FileSystemObject] =
    Encoder.instance[FileSystemObject] {
      case Directory(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.Directory.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
        )

      case File(name, path) =>
        Json.obj(
          CodecField.Type -> CodecType.File.asJson,
          CodecField.Name -> name.asJson,
          CodecField.Path -> path.asJson
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
}
