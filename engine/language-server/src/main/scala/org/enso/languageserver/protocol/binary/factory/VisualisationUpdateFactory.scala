package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.executioncontext
import org.enso.languageserver.runtime.VisualisationProtocol.{
  VisualisationContext,
  VisualisationUpdate
}

object VisualisationUpdateFactory {

  def create(event: VisualisationUpdate)(
    implicit builder: FlatBufferBuilder
  ): Int = {
    val ctx = createVisualisationCtx(event.visualisationContext)
    val data =
      executioncontext.VisualisationUpdate.createDataVector(builder, event.data)
    executioncontext.VisualisationUpdate.createVisualisationUpdate(
      builder,
      ctx,
      data
    )
  }

  def createVisualisationCtx(ctx: VisualisationContext)(
    implicit builder: FlatBufferBuilder
  ): Int = {
    executioncontext.VisualisationContext.startVisualisationContext(builder)
    executioncontext.VisualisationContext
      .addContextId(builder, EnsoUuidFactory.create(ctx.contextId))
    executioncontext.VisualisationContext
      .addExpressionId(builder, EnsoUuidFactory.create(ctx.expressionId))
    executioncontext.VisualisationContext
      .addVisualisationId(builder, EnsoUuidFactory.create(ctx.visualisationId))
    executioncontext.VisualisationContext.endVisualisationContext(builder)
  }

}
