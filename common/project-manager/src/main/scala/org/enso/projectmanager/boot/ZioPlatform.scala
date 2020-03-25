package org.enso.projectmanager.boot

import zio.Cause
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig
import zio.internal.{Executor, Platform, Tracing}

import scala.concurrent.ExecutionContext

class ZioPlatform(computeEc: ExecutionContext) extends Platform {

  override def executor: Executor =
    Executor.fromExecutionContext(2048)(computeEc)

  override val tracing = Tracing(
    Tracer.globallyCached(new AkkaLineNumbersTracer),
    TracingConfig.enabled
  )

  override def fatal(t: Throwable): Boolean =
    t.isInstanceOf[VirtualMachineError]

  override def reportFatal(t: Throwable): Nothing = {
    t.printStackTrace()
    try {
      System.exit(-1)
      throw t
    } catch { case _: Throwable => throw t }
  }

  override def reportFailure(cause: Cause[Any]): Unit =
    if (cause.died)
      System.err.println(cause.prettyPrint)

}
