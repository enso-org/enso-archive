package org.enso.languageserver.io

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.ByteString
import org.enso.languageserver.data.ClientId
import org.enso.languageserver.event.{
  ExecutionContextCreated,
  ExecutionContextDestroyed,
  ExecutionContextEvent
}
import org.enso.languageserver.io.InputOutputProtocol.{
  FeedStandardInput,
  WaitingForStandardInput
}
import org.enso.languageserver.io.InputRedirectionController.ContextData
import org.enso.languageserver.io.ObservablePipedInputStream.{
  InputObserver,
  InputStreamEvent,
  ReadBlocked
}
import org.enso.languageserver.session.SessionRouter.DeliverToJsonController
import org.enso.languageserver.util.UnhandledLogging

class InputRedirectionController(
  stdIn: ObservablePipedInputStream,
  stdInSink: ObservableOutputStream,
  sessionRouter: ActorRef
) extends Actor
    with ActorLogging
    with UnhandledLogging
    with InputObserver {

  override def preStart(): Unit = {
    stdIn.attach(this)
    context.system.eventStream.subscribe(self, classOf[ExecutionContextEvent])
  }

  override def receive: Receive = running()

  private def running(liveContexts: Set[ContextData] = Set.empty): Receive = {
    case FeedStandardInput(input, isLineTerminated) =>
      if (isLineTerminated) {
        val bytes =
          ByteString.createBuilder
            .append(ByteString.fromArray(input.getBytes))
            .append(ByteString.fromArray(System.lineSeparator().getBytes))
            .result()

        stdInSink.write(bytes.toArray)
      } else {
        stdInSink.write(input.getBytes)
      }

    case ExecutionContextCreated(contextId, owner) =>
      context.become(running(liveContexts + ContextData(contextId, owner)))

    case ExecutionContextDestroyed(contextId, owner) =>
      context.become(running(liveContexts - ContextData(contextId, owner)))

    case ReadBlocked =>
      log.debug("Blocked read detected")
      liveContexts foreach {
        case ContextData(_, owner) =>
          sessionRouter ! DeliverToJsonController(
            owner,
            WaitingForStandardInput
          )
      }
  }

  override def update(event: InputStreamEvent): Unit = { self ! event }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    stdIn.detach(this)
  }

  override def postStop(): Unit = {
    stdIn.detach(this)
  }

}

object InputRedirectionController {

  private case class ContextData(contextId: UUID, owner: ClientId)

  def props(
    stdIn: ObservablePipedInputStream,
    sink: ObservableOutputStream,
    sessionRouter: ActorRef
  ): Props =
    Props(new InputRedirectionController(stdIn, sink, sessionRouter))

}
