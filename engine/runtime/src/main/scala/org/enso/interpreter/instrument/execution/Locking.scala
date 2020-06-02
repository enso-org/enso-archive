package org.enso.interpreter.instrument.execution

import java.io.File
import java.util.UUID

trait Locking {

  def removeContextLock(contextId: UUID): Unit

  def removeFileLock(file: File): Unit

  def acquireWriteCompilationLock(): Unit

  def releaseWriteCompilationLock(): Unit

  def acquireReadCompilationLock(): Unit

  def releaseReadCompilationLock(): Unit

  def acquireContextLock(contextId: UUID): Unit

  def releaseContextLock(contextId: UUID): Unit

  def acquireFileLock(file: File): Unit

  def releaseFileLock(file: File): Unit

}
