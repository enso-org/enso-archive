package org.enso.languageserver.filemanager

import java.io.File

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

/**
  * A representation of filesystem event.
  *
  * @param path path to the file system object
  * @param kind type of file system event
  */
case class FileEvent(path: Path, kind: FileEventKind)

object FileEvent {

  /**
    * Conversion from file system event.
    *
    * @param root a project root
    * @param base a watched path
    * @param event a file system event
    * @return file event
    */
  def fromWatcherEvent(
    root: File,
    base: Path,
    event: FileEventWatcher.WatcherEvent
  ): FileEvent =
    FileEvent(
      Path.getRelativePath(root, base, event.path),
      FileEventKind(event.eventType)
    )
}

/**
  * Type of a file event.
  */
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

  /**
    * Create [[FileEventKind]] from [[FileEventWatcher.EventType]].
    *
    * @param eventType file system event type
    * @return file event kind
    */
  def apply(eventType: FileEventWatcher.EventType): FileEventKind =
    eventType match {
      case FileEventWatcher.EventTypeCreate => FileEventKind.Added
      case FileEventWatcher.EventTypeModify => FileEventKind.Modified
      case FileEventWatcher.EventTypeDelete => FileEventKind.Removed
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
