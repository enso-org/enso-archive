package org.enso.languageserver.filemanager

import java.nio.file.Path

import io.methvin.watcher.DirectoryChangeEvent

object FileEventWatcherApi {

  /**
    * Type of a file event.
    */
  sealed trait EventType

  object EventType {

    /**
      * Creates [[EventType]] from file system event type.
      *
      * @param eventType file system event type
      * @return watcher event type
      */
    def from(eventType: DirectoryChangeEvent.EventType): Option[EventType] =
      eventType match {
        case DirectoryChangeEvent.EventType.CREATE   => Some(EventTypeCreate)
        case DirectoryChangeEvent.EventType.MODIFY   => Some(EventTypeModify)
        case DirectoryChangeEvent.EventType.DELETE   => Some(EventTypeDelete)
        case DirectoryChangeEvent.EventType.OVERFLOW => None
      }
  }

  /**
    * Event type indicating file creation.
    */
  case object EventTypeCreate extends EventType

  /**
    * Event type indicating file modification.
    */
  case object EventTypeModify extends EventType

  /**
    * Event type indicating file deletion.
    */
  case object EventTypeDelete extends EventType

  /**
    * Object representing file system event.
    *
    * @param path path to the file system object
    * @param eventType event type
    */
  case class WatcherEvent(path: Path, eventType: EventType)

  object WatcherEvent {

    /**
      * Conversion form file system event to [[WatcherEvent]]
      *
      * @param event file system event
      * @return watcher event
      */
    def from(event: DirectoryChangeEvent): Option[WatcherEvent] =
      EventType
        .from(event.eventType())
        .map(WatcherEvent(event.path(), _))

  }
}
