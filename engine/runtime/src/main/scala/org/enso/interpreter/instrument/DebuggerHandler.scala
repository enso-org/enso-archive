package org.enso.interpreter.instrument

import java.nio.ByteBuffer
import org.enso.interpreter.instrument.ReplDebuggerInstrument.ReplExecutionEventNode
import org.enso.polyglot.debugger.{
  Debugger,
  EvaluationRequest,
  ListBindingsRequest,
  Request,
  SessionExitRequest
}
import org.graalvm.polyglot.io.MessageEndpoint

class DebuggerEndpoint(handler: DebuggerHandler) extends MessageEndpoint {
  var client: MessageEndpoint = _

  def setClient(ep: MessageEndpoint): Unit = client = ep

  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit = {
    Debugger.deserializeRequest(data).foreach(handler.onMessage)
  }

  override def sendPing(data: ByteBuffer): Unit = client.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}
}

class DebuggerHandler {
  val endpoint = new DebuggerEndpoint(this)

  def sendToClient(data: ByteBuffer): Unit = {
    endpoint.client.sendBinary(data)
  }

  def hasClient: Boolean = endpoint.client != null

  var currentExecutionNode: ReplExecutionEventNode = _

  def startSession(executionNode: ReplExecutionEventNode): Unit = {
    currentExecutionNode = executionNode
    sendToClient(Debugger.createSessionStartNotification())
  }

  def onMessage(request: Request): Unit = request match {
    case EvaluationRequest(expression) =>
      val result = currentExecutionNode.evaluate(expression)
      result match {
        case Left(error) =>
          sendToClient(Debugger.createEvaluationFailure(error))
        case Right(value) =>
          sendToClient(Debugger.createEvaluationSuccess(value))
      }
    case ListBindingsRequest =>
      val bindings = currentExecutionNode.listBindings()
      sendToClient(Debugger.createListBindingsResult(bindings))
    case SessionExitRequest =>
      currentExecutionNode.exit()
      currentExecutionNode = null
      sendToClient(Debugger.createSessionExitSuccess())
  }
}
