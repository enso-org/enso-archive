package org.enso.languageserver.io

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString
import org.enso.languageserver.io.InputOutputProtocol.FeedStandardInput
import org.enso.languageserver.io.ObservablePipedInputStream.{
  InputObserver,
  InputStreamEvent,
  ReadBlocked
}
import org.enso.languageserver.util.UnhandledLogging

class InputRedirectionController(
  stdIn: ObservablePipedInputStream,
  stdInSink: ObservableOutputStream
) extends Actor
    with ActorLogging
    with UnhandledLogging
    with InputObserver {

  override def preStart(): Unit = {
    stdIn.attach(this)
  }

  override def receive: Receive = {
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

    case ReadBlocked =>
      println("blocking read detected")
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

  def props(
    stdIn: ObservablePipedInputStream,
    sink: ObservableOutputStream
  ): Props =
    Props(new InputRedirectionController(stdIn, sink))

}
