package org.enso.polyglot.debugger

import java.nio.ByteBuffer
import scala.jdk.CollectionConverters._

import com.google.flatbuffers.FlatBufferBuilder
import org.enso.polyglot.debugger.protocol.factory.{
  RequestFactory,
  ResponseFactory
}
import org.enso.polyglot.debugger.protocol.{
  BinaryRequest,
  BinaryResponse,
  RequestPayload,
  ResponsePayload
}

object Debugger {
  def deserializeRequest(bytes: ByteBuffer): Option[Request] =
    try {
      val inMsg = BinaryRequest.getRootAsBinaryRequest(bytes)

      inMsg.payloadType() match {
        case RequestPayload.evaluate =>
          val evaluationRequest = inMsg
            .payload(new protocol.EvaluationRequest())
            .asInstanceOf[protocol.EvaluationRequest]
          Some(EvaluationRequest(evaluationRequest.expression()))
        case RequestPayload.listBindings =>
          Some(ListBindingsRequest)
        case RequestPayload.exit =>
          Some(ExitRequest)
        case _ => None
      }
    } catch {
      case _: Exception => None
    }

  def deserializeResponse(bytes: ByteBuffer): Option[Response] =
    try {
      val inMsg = BinaryResponse.getRootAsBinaryResponse(bytes)

      inMsg.payloadType() match {
        case ResponsePayload.evaluationSuccess =>
          val evaluationResult = inMsg
            .payload(new protocol.EvaluationSuccess())
            .asInstanceOf[protocol.EvaluationSuccess]
          Some(EvaluationSuccess(evaluationResult.result()))
        case ResponsePayload.evaluationFailure =>
          val evaluationResult = inMsg
            .payload(new protocol.EvaluationFailure())
            .asInstanceOf[protocol.EvaluationFailure]
          Some(EvaluationFailure(evaluationResult.exception()))
        case ResponsePayload.listBindings =>
          val bindingsResult = inMsg
            .payload(new protocol.ListBindingsResult())
            .asInstanceOf[protocol.ListBindingsResult]
          val bindings =
            for (i <- 0 until bindingsResult.bindingsLength()) yield {
              val binding = bindingsResult.bindings(i)
              (binding.name(), binding.value())
            }
          Some(ListBindingsResult(bindings.toMap))
        case ResponsePayload.exit =>
          Some(ExitSuccess)
        case _ => None
      }
    } catch {
      case _: Exception => None
    }

  def createEvaluationRequest(expression: String): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val requestOffset                       = RequestFactory.createEvaluationRequest(expression)
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      RequestPayload.evaluate,
      requestOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createListBindingsRequest(): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val requestOffset                       = RequestFactory.createListBindingsRequest()
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      RequestPayload.listBindings,
      requestOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createExitRequest(): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val requestOffset                       = RequestFactory.createExitRequest()
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      RequestPayload.exit,
      requestOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createEvaluationSuccess(result: Object): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val replyOffset                         = ResponseFactory.createEvaluationSuccess(result)
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      ResponsePayload.evaluationSuccess,
      replyOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createEvaluationFailure(exception: Exception): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val replyOffset                         = ResponseFactory.createEvaluationFailure(exception)
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      ResponsePayload.evaluationFailure,
      replyOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createListBindingsResult(bindings: Map[String, Object]): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val replyOffset                         = ResponseFactory.createListBindingsResult(bindings)
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      ResponsePayload.listBindings,
      replyOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }

  def createListBindingsResult(
    bindings: java.util.Map[String, Object]
  ): ByteBuffer =
    createListBindingsResult(bindings.asScala.toMap)

  def createExitSuccess(): ByteBuffer = {
    implicit val builder: FlatBufferBuilder = new FlatBufferBuilder(256)
    val replyOffset                         = ResponseFactory.createExitSuccess()
    val outMsg = BinaryRequest.createBinaryRequest(
      builder,
      ResponsePayload.exit,
      replyOffset
    )
    builder.finish(outMsg)
    builder.dataBuffer()
  }
}
