package org.enso.interpreter.instrument

import com.oracle.truffle.api.TruffleContext
import org.enso.interpreter.service.ExecutionService

case class RuntimeContext(
  executionService: ExecutionService,
  contextManager: ExecutionContextManager,
  endpoint: Endpoint,
  truffleContext: TruffleContext,
  cache: Cache
)
