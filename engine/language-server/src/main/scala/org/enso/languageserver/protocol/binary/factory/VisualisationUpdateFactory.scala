package org.enso.languageserver.protocol.binary.factory

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.languageserver.protocol.binary.executioncontext
import org.enso.languageserver.runtime.VisualisationProtocol.{
  VisualisationContext,
  VisualisationUpdate
}

object VisualisationUpdateFactory {

  /**
    * Creates [[VisualisationUpdate]] inside a [[FlatBufferBuilder]].
    *
    * @param update a visualisation update
    * @param builder a flat buffers builder
    * @return offset
    */
  def create(update: VisualisationUpdate)(
    implicit builder: FlatBufferBuilder
  ): Int = {
    val ctx = createVisualisationCtx(update.visualisationContext)
    val data =
      executioncontext.VisualisationUpdate
        .createDataVector(builder, update.data)
    executioncontext.VisualisationUpdate.createVisualisationUpdate(
      builder,
      ctx,
      data
    )
  }

  /**
    * Creates [[VisualisationContext]] inside a [[FlatBufferBuilder]].
    *
    * @param ctx a VisualisationContext
    * @param builder a flat buffers builder
    * @return offset
    */
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
