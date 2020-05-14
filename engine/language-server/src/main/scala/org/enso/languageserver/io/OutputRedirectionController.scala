package org.enso.languageserver.io

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.languageserver.data.ClientId
import org.enso.languageserver.event.JsonSessionTerminated
import org.enso.languageserver.io.InputOutputProtocol.{
  OutputAppended,
  RedirectOutput,
  SuppressOutput
}
import org.enso.languageserver.io.ObservableCharOutput.Subscriber
import org.enso.languageserver.io.OutputRedirectionController.CharSequenceAppended
import org.enso.languageserver.session.SessionRouter.DeliverToJsonController
import org.enso.languageserver.util.UnhandledLogging

class OutputRedirectionController(
  charOutput: ObservableCharOutput,
  outputKind: OutputKind,
  sessionRouter: ActorRef
) extends Actor
    with ActorLogging
    with UnhandledLogging
    with Subscriber {

  override def preStart(): Unit = {
    charOutput.subscribe(this)
    context.system.eventStream.subscribe(self, classOf[JsonSessionTerminated])
  }

  override def receive: Receive = running()

  private def running(subscribers: Set[ClientId] = Set.empty): Receive = {
    case CharSequenceAppended(charSequence) =>
      subscribers foreach { subscriber =>
        sessionRouter ! DeliverToJsonController(
          subscriber,
          OutputAppended(charSequence, outputKind)
        )
      }

    case RedirectOutput(clientId) =>
      context.become(running(subscribers + clientId))

    case SuppressOutput(clientId) =>
      context.become(running(subscribers - clientId))

    case JsonSessionTerminated(session) =>
      context.become(running(subscribers - session.clientId))
  }

  override def update(charSequence: String): Unit =
    self ! CharSequenceAppended(charSequence)

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    charOutput.unsubscribe(this)
  }

  override def postStop(): Unit = {
    charOutput.unsubscribe(this)
  }

}

object OutputRedirectionController {

  private case class CharSequenceAppended(charSequence: String)

  def props(
    stdOut: ObservableCharOutput,
    outputKind: OutputKind,
    sessionRouter: ActorRef
  ): Props =
    Props(new OutputRedirectionController(stdOut, outputKind, sessionRouter))

}
