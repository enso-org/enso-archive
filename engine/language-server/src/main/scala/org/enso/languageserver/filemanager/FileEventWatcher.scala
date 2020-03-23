package org.enso.languageserver.filemanager

import java.nio.file.Path

import io.methvin.watcher._

/**
  * Watches the root with subdirectories and executes callback on file event.
  *
  * @param root directory to watch
  * @param callback that fires on the file system events
  */
final class FileEventWatcher(
  root: Path,
  callback: FileEventWatcherApi.WatcherEvent => Unit
) extends DirectoryChangeListener {

  import FileEventWatcherApi._

  private val watcher: DirectoryWatcher = DirectoryWatcher
    .builder()
    .path(root)
    .listener(this)
    .build()

  def start(): Unit =
    watcher.watch()

  def stop(): Unit =
    watcher.close()

  override def onEvent(event: DirectoryChangeEvent): Unit = {
    WatcherEvent
      .from(event)
      .foreach(callback)
  }
}

object FileEventWatcherApi {

  sealed trait EventType

  object EventType {

    def from(eventType: DirectoryChangeEvent.EventType): Option[EventType] =
      eventType match {
        case DirectoryChangeEvent.EventType.CREATE   => Some(EventTypeCreate)
        case DirectoryChangeEvent.EventType.MODIFY   => Some(EventTypeModify)
        case DirectoryChangeEvent.EventType.DELETE   => Some(EventTypeDelete)
        case DirectoryChangeEvent.EventType.OVERFLOW => None
      }
  }

  case object EventTypeCreate extends EventType

  case object EventTypeModify extends EventType

  case object EventTypeDelete extends EventType

  case class WatcherEvent(path: Path, eventType: EventType)

  object WatcherEvent {

    def from(event: DirectoryChangeEvent): Option[WatcherEvent] =
      EventType
        .from(event.eventType())
        .map(WatcherEvent(event.path(), _))

  }
}
