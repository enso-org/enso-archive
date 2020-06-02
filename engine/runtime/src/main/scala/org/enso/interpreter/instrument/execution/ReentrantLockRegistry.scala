package org.enso.interpreter.instrument.execution

import java.io.File
import java.util.UUID
import java.util.concurrent.locks.{
  Lock,
  ReadWriteLock,
  ReentrantLock,
  ReentrantReadWriteLock
}

class ReentrantLockRegistry extends LockRegistry {

  private val compilationLock = new ReentrantReadWriteLock(true)

  private val contextMapLock = new ReentrantLock()

  private var contextLocks = Map.empty[UUID, ReentrantLock]

  private val fileMapLock = new ReentrantLock()

  private var fileLocks = Map.empty[File, ReentrantLock]

  override def getCompilationLock(): ReadWriteLock = compilationLock

  override def getContextLock(contextId: UUID): Lock = {
    contextMapLock.lock()
    try {
      if (contextLocks.contains(contextId)) {
        contextLocks(contextId)
      } else {
        val lock = new ReentrantLock(true)
        contextLocks += (contextId -> lock)
        lock
      }
    } finally {
      contextMapLock.unlock()
    }
  }

  override def removeContextLock(contextId: UUID): Unit = {
    contextMapLock.lock()
    try {
      contextLocks -= contextId
    } finally {
      contextMapLock.unlock()
    }
  }

  override def getFileLock(file: File): Lock = {
    fileMapLock.lock()
    try {
      if (fileLocks.contains(file)) {
        fileLocks(file)
      } else {
        val lock = new ReentrantLock(true)
        fileLocks += (file -> lock)
        lock
      }
    } finally {
      fileMapLock.unlock()
    }
  }
}
