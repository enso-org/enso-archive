package org.enso.languageserver.filemanager

import java.io.File

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

/**
  * A representation of filesystem event.
  *
  * @param object filesystem object
  * @param kind type of filesystem event
  */
case class FileEvent(path: Path, kind: FileEventKind)

object FileEvent {

  def fromWatcherEvent(
    root: File,
    base: Path,
    event: FileEventWatcherApi.WatcherEvent
  ): FileEvent =
    FileEvent(
      Path.getRelativePath(root, base, event.path),
      FileEventKind(event.eventType)
    )
}

sealed trait FileEventKind

object FileEventKind {

  /**
    * Event type indicating file creation.
    */
  case object Added extends FileEventKind

  /**
    * Event type indicating file deletion.
    */
  case object Removed extends FileEventKind

  /**
    * Event type indicating file modification.
    */
  case object Modified extends FileEventKind

  def apply(eventType: FileEventWatcherApi.EventType): FileEventKind =
    eventType match {
      case FileEventWatcherApi.EventTypeCreate => FileEventKind.Added
      case FileEventWatcherApi.EventTypeModify => FileEventKind.Modified
      case FileEventWatcherApi.EventTypeDelete => FileEventKind.Removed
    }

  private object CodecField {

    val Type = "type"
  }

  private object CodecType {

    val Added = "Added"

    val Removed = "Removed"

    val Modified = "Modified"
  }

  implicit val encoder: Encoder[FileEventKind] =
    Encoder.instance[FileEventKind] {
      case Added =>
        Json.obj(CodecField.Type -> CodecType.Added.asJson)

      case Removed =>
        Json.obj(CodecField.Type -> CodecType.Removed.asJson)

      case Modified =>
        Json.obj(CodecField.Type -> CodecType.Modified.asJson)
    }

  implicit val decoder: Decoder[FileEventKind] =
    Decoder.instance { cursor =>
      cursor.downField(CodecField.Type).as[String].flatMap {
        case CodecType.Added    => Right(Added)
        case CodecType.Removed  => Right(Removed)
        case CodecType.Modified => Right(Modified)
      }
    }
}
