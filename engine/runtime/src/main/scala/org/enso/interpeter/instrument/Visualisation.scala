package org.enso.interpeter.instrument

import org.enso.polyglot.runtime.Runtime.Api.{ExpressionId, VisualisationId}

/**
  * An object containing visualisation data.
  *
  * @param id the unique identifier of visualisation
  * @param expressionId the identifier of expression that the visualisation is
  *                     attached to
  * @param expression the expression used to generate visualisation data
  */
case class Visualisation(
  id: VisualisationId,
  expressionId: ExpressionId,
  expression: AnyRef
)
