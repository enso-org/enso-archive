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
    .path(root.toRealPath())
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
    * A callback executed by [[DirectoryWatcher]] on file system event.
    */
  override def onEvent(event: DirectoryChangeEvent): Unit = {
    WatcherEvent
      .from(event)
      .foreach(callback)
  }
}

object FileEventWatcher {

  def build(root: Path, callback: FileEventWatcherApi.WatcherEvent => Unit): FileEventWatcher =
    new FileEventWatcher(root, callback)
}
