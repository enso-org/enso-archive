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
  eventCallback: FileEventWatcher.WatcherEvent => Unit,
  errorCallback: FileEventWatcher.WatcherError => Unit
) extends DirectoryChangeListener {

  import FileEventWatcher._

  private val watcher: DirectoryWatcher = DirectoryWatcher
    .builder()
    .path(root)
    .listener(this)
    .build()

  /**
    * Start the watcher.
    */
  def start(): Unit =
    watcher.watch()

  /**
    * Stop the watcher.
    */
  def stop(): Unit =
    watcher.close()

  /**
    * A callback executed by `DirectoryWatcher` on file system event.
    */
  override def onEvent(event: DirectoryChangeEvent): Unit = {
    WatcherEvent
      .from(event)
      .foreach(eventCallback)
  }

  override def onException(e: Exception): Unit = {
    errorCallback(FileEventWatcher.WatcherError(e))
  }
}

object FileEventWatcher {

  /**
    * Type of a file event.
    */
  sealed trait EventType

  private object EventType {

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

  /**
    * Object representing en error.
    *
    * @param exception an error
    */
  case class WatcherError(exception: Exception)

  def build(
    root: Path,
    eventCallback: WatcherEvent => Unit,
    exceptionCallback: WatcherError => Unit
  ): FileEventWatcher =
    new FileEventWatcher(root, eventCallback, exceptionCallback)

}
