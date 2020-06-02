package org.enso.interpreter.instrument.execution

import java.io.File
import java.util.UUID
import java.util.concurrent.locks.{Lock, ReadWriteLock}

trait LockRegistry {

  def getCompilationLock(): ReadWriteLock

  def getContextLock(contextId: UUID): Lock

  def removeContextLock(contextId: UUID): Unit

  def getFileLock(file: File): Lock

}
