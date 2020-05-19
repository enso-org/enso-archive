package org.enso.languageserver.requesthandler.io

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.jsonrpc.{Request, ResponseResult, Unused}
import org.enso.languageserver.data.ClientId
import org.enso.languageserver.io.InputOutputApi.SuppressStandardError
import org.enso.languageserver.io.InputOutputProtocol
import org.enso.languageserver.util.UnhandledLogging

class SuppressStdErrHandler(stdErrController: ActorRef, clientId: ClientId)
    extends Actor
    with ActorLogging
    with UnhandledLogging {

  override def receive: Receive = {
    case Request(SuppressStandardError, id, _) =>
      stdErrController ! InputOutputProtocol.SuppressOutput(clientId)
      sender() ! ResponseResult(SuppressStandardError, id, Unused)
      context.stop(self)
  }

}

object SuppressStdErrHandler {

  def props(stdErrController: ActorRef, clientId: ClientId): Props =
    Props(new SuppressStdErrHandler(stdErrController, clientId))

}
