package org.enso.languageserver.event

import org.enso.languageserver.filemanager.Path

/**
  * Base trait for all buffer events.
  */
sealed trait BufferEvent extends Event

/**
  * Notifies the Language Server when new buffer is created.
  *
  * @param path the path to a file
  */
case class BufferCreated(path: Path) extends BufferEvent

/**
  * Notifies the Language Server when buffer is created.
  *
  * @param path the path to a file
  */
case class BufferClosed(path: Path) extends BufferEvent
