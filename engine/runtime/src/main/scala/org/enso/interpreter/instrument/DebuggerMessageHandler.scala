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

class DebuggerEndpoint(handler: DebuggerMessageHandler)
    extends MessageEndpoint {
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

class DebuggerMessageHandler {
  val endpoint = new DebuggerEndpoint(this)

  def sendToClient(data: ByteBuffer): Unit = {
    endpoint.client.sendBinary(data)
  }

  def hasClient: Boolean = endpoint.client != null

  private val executionNodeStack
    : collection.mutable.Stack[ReplExecutionEventNode] =
    collection.mutable.Stack.empty

  private def currentExecutionNode: Option[ReplExecutionEventNode] =
    executionNodeStack.headOption

  def startSession(executionNode: ReplExecutionEventNode): Unit = {
    executionNodeStack.push(executionNode)
    sendToClient(Debugger.createSessionStartNotification())
  }

  /**
    * A helper function that cleans up the current session and terminates it.
    *
    * @return never returns as control is passed to the interpreter
    */
  def endSession(): Nothing = {
    val node = executionNodeStack.pop()
    node.exit()
    throw new IllegalStateException(
      "exit() on execution node returned unexpectedly"
    )
  }

  def onMessage(request: Request): Unit =
    currentExecutionNode match {
      case Some(node) =>
        request match {
          case EvaluationRequest(expression) =>
            val result = node.evaluate(expression)
            result match {
              case Left(error) =>
                sendToClient(Debugger.createEvaluationFailure(error))
              case Right(value) =>
                sendToClient(Debugger.createEvaluationSuccess(value))
            }
          case ListBindingsRequest =>
            val bindings = node.listBindings()
            sendToClient(Debugger.createListBindingsResult(bindings))
          case SessionExitRequest =>
            endSession()
        }
      case None =>
        throw new IllegalStateException(
          "Got a request but no session is running"
        )
    }
}
