package org.enso.polyglot.debugger

import java.nio.ByteBuffer

import org.enso.polyglot.debugger.protocol.{
  ExceptionRepresentation,
  ObjectRepresentation
}
import org.graalvm.polyglot.io.MessageEndpoint

/**
  * Class that can be returned by serverTransport to establish communication
  * with Debugger Instrument.
  */
class DebuggerSessionManagerEndpoint(
  val sessionManager: SessionManager,
  val peer: MessageEndpoint
) extends MessageEndpoint {
  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit =
    Debugger.deserializeResponse(data) match {
      case Some(response) =>
        println(s"Got $response")
        handleResponse(response)
      case None =>
        throw new RuntimeException(
          "Failed to deserialize response from the debugger"
        )
    }

  override def sendPing(data: ByteBuffer): Unit = peer.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}

  private var currentExecutor: ReplExecutorImplementation = _

  private def handleResponse(response: Response): Unit = {
    if (currentExecutor != null) {
      currentExecutor.onResponse(response)
    } else if (response == SessionStartNotification) {
      currentExecutor = new ReplExecutorImplementation
      println("Starting a new session")
      sessionManager.startSession(currentExecutor)
      currentExecutor = null
      println("Session terminated")
    } else {
      throw new RuntimeException(
        s"Unexpected response $response, no session is initialized"
      )
    }
  }

  private class ReplExecutorImplementation extends ReplExecutor {
    override type SessionEnded = Unit

    var evaluationResult
      : Either[ExceptionRepresentation, ObjectRepresentation] = _
    override def evaluate(
      expression: String
    ): Either[ExceptionRepresentation, ObjectRepresentation] = {
      ensureUsable()
      evaluationResult = null
      peer.sendBinary(Debugger.createEvaluationRequest(expression))
      if (evaluationResult == null)
        throw new RuntimeException(
          "DebuggerServer returned but did not send back expected result"
        )
      else
        evaluationResult
    }

    var bindingsResult: Map[String, ObjectRepresentation] = _
    override def listBindings(): Map[String, ObjectRepresentation] = {
      ensureUsable()
      bindingsResult = null
      peer.sendBinary(Debugger.createListBindingsRequest())
      if (bindingsResult == null)
        throw new RuntimeException(
          "DebuggerServer returned but did not send back expected result"
        )
      else
        bindingsResult
    }

    var exitSuccess: Boolean = false
    private def ensureUsable(): Unit = {
      if (exitSuccess) {
        throw new IllegalStateException(
          "Cannot use the executor after exit() has been called"
        )
      }
    }
    override def exit(): SessionEnded = {
      ensureUsable()
      currentExecutor = null
      // TODO seems like this never returns ?
      peer.sendBinary(Debugger.createSessionExitRequest())
      if (!exitSuccess) {
        throw new RuntimeException(
          "DebuggerServer returned but did not confirm Session Exit Success"
        )
      }
    }

    def onResponse(response: Response): Unit = {
      println(s"Response: $response")
      response match {
        case EvaluationSuccess(result)    => evaluationResult = Right(result)
        case EvaluationFailure(exception) => evaluationResult = Left(exception)
        case ListBindingsResult(bindings) => bindingsResult   = bindings
        case SessionExitSuccess           => exitSuccess      = true
        case SessionStartNotification =>
          throw new RuntimeException(
            "Session start notification sent while the session is already running"
          )
      }
    }
  }
}
