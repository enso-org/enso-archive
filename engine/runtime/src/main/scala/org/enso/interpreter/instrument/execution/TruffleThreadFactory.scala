package org.enso.interpreter.instrument.execution

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import org.enso.interpreter.runtime.Context

class TruffleThreadFactory(context: Context, prefix: String)
    extends ThreadFactory {

  private val counter = new AtomicInteger(0)

  override def newThread(r: Runnable): Thread = {
    val thread = context.createThread(r)
    thread.setName(s"$prefix-${counter.incrementAndGet()}")

    thread
  }

}
