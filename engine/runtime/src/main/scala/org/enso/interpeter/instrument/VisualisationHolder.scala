package org.enso.interpeter.instrument

import org.enso.polyglot.runtime.Runtime.Api.{ExpressionId, VisualisationId}

class VisualisationHolder() {

  private var visualisationMap: Map[ExpressionId, List[Visualisation]] =
    Map.empty.withDefaultValue(List.empty)

  def upsert(visualisation: Visualisation): Unit = {
    val visualisations = visualisationMap(visualisation.expressionId)
    val removed        = visualisations.filterNot(_.id == visualisation.id)
    visualisationMap += (visualisation.expressionId -> (visualisation :: removed))
  }

  def remove(
    visualisationId: VisualisationId,
    expressionId: ExpressionId
  ): Unit = {
    val visualisations = visualisationMap(expressionId)
    val removed        = visualisations.filterNot(_.id == visualisationId)
    visualisationMap += (expressionId -> removed)
  }

  def find(expressionId: ExpressionId): List[Visualisation] =
    visualisationMap(expressionId)

  def getById(visualisationId: VisualisationId): Option[Visualisation] =
    visualisationMap.values.flatten.find(_.id == visualisationId)

}

object VisualisationHolder {

  def empty = new VisualisationHolder()

}
