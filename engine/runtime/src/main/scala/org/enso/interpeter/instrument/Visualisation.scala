package org.enso.interpeter.instrument

import org.enso.interpreter.runtime.Module
import org.enso.polyglot.runtime.Runtime.Api.{ExpressionId, VisualisationId}

case class Visualisation(
  id: VisualisationId,
  expressionId: ExpressionId,
  module: Module,
  expression: String
)
