package org.enso.languageserver.filemanager

import io.circe.Decoder
import io.circe.generic.auto._

sealed trait FileSystemObject

object FileSystemObject {

  case class Directory(name: String, path: Path) extends FileSystemObject

  case class File(name: String, path: Path) extends FileSystemObject

  private val TypeField = "type"

  private val NameField = "name"

  private val PathField = "path"

  implicit val fsoDecoder: Decoder[FileSystemObject] =
    Decoder.instance { cursor =>
      cursor.downField(TypeField).as[String].flatMap {
        case "File" =>
          for {
            name <- cursor.downField(NameField).as[String]
            path <- cursor.downField(PathField).as[Path]
          } yield File(name, path)

        case "Directory" =>
          for {
            name <- cursor.downField(NameField).as[String]
            path <- cursor.downField(PathField).as[Path]
          } yield Directory(name, path)
      }
    }

}
