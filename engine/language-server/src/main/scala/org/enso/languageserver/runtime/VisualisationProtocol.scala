package org.enso.languageserver.runtime

import java.util.UUID

object VisualisationProtocol {

  case class VisualisationContext(
    visualisationId: UUID,
    contextId: UUID,
    expressionId: UUID
  )

  case class VisualisationUpdate(
    visualisationContext: VisualisationContext,
    data: Array[Byte]
  )

}
