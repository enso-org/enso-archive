package org.enso.interpreter.instrument

import com.oracle.truffle.api.TruffleContext
import org.enso.interpreter.service.ExecutionService

case class InterpreterContext(
  executionService: ExecutionService,
  contextManager: ExecutionContextManager,
  endpoint: Endpoint,
  truffleContext: TruffleContext,
  cache: Cache
)
