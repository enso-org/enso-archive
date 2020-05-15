package org.enso.languageserver.requesthandler.io

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.jsonrpc.{Request, ResponseResult, Unused}
import org.enso.languageserver.data.ClientId
import org.enso.languageserver.io.InputOutputApi.RedirectStandardOutput
import org.enso.languageserver.io.InputOutputProtocol
import org.enso.languageserver.util.UnhandledLogging

class RedirectStdOutHandler(stdOutController: ActorRef, clientId: ClientId)
    extends Actor
    with ActorLogging
    with UnhandledLogging {

  override def receive: Receive = {
    case Request(RedirectStandardOutput, id, _) =>
      stdOutController ! InputOutputProtocol.RedirectOutput(clientId)
      sender() ! ResponseResult(RedirectStandardOutput, id, Unused)
  }

}

object RedirectStdOutHandler {

  def props(stdOutController: ActorRef, clientId: ClientId): Props =
    Props(new RedirectStdOutHandler(stdOutController, clientId))

}
