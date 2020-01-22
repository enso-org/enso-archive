package org.enso

import akka.actor.{Actor, ActorLogging, Props}
import org.enso.languageserver.{
  NotificationReceived,
  Notifications,
  RequestReceived,
  Requests
}
import org.enso.polyglot.ExecutionContext

/** The language server component wraps the runtime itself, and uses the APIs
  * provided by the interpreter and the compiler to service the requests sent
  * to the Enso Engine.
  */
class LanguageServer(context: ExecutionContext)
    extends Actor
    with ActorLogging {
  override def receive: Receive = {
    case Requests.Initialize(id, _, actorRef) =>
      val msg = "LanguageServer: Initialize received"
      log.info(msg)
      sender() ! RequestReceived.Initialize(id, actorRef)

    case Requests.Shutdown(id, actorRef) =>
      val msg = "LanguageServer: Shutdown received"
      log.info(msg)
      sender() ! RequestReceived.Shutdown(id, actorRef)

    case Requests.ApplyWorkspaceEdit(id, actorRef) =>
      val msg = "LanguageServer: ApplyWorkspaceEdit received"
      log.info(msg)
      sender() ! RequestReceived.ApplyWorkspaceEdit(id, actorRef)

    case Requests.WillSaveTextDocumentWaitUntil(id, actorRef) =>
      val msg = "LanguageServer: WillSaveTextDocumentWaitUntilEdit received"
      log.info(msg)
      sender() ! RequestReceived.WillSaveTextDocumentWaitUntil(id, actorRef)

    case Notifications.Initialized =>
      val msg = "LanguageServer: Initialized received"
      log.info(msg)
      sender() ! NotificationReceived.Initialized

    case Notifications.Exit =>
      val msg = "LanguageServer: Exit received"
      log.info(msg)
      sender() ! NotificationReceived.Exit

    case Notifications.DidOpenTextDocument =>
      val msg = "LanguageServer: DidOpenTextDocument received"
      log.info(msg)
      sender() ! NotificationReceived.DidOpenTextDocument

    case Notifications.DidChangeTextDocument =>
      val msg = "LanguageServer: DidChangeTextDocument received"
      log.info(msg)
      sender() ! NotificationReceived.DidChangeTextDocument

    case Notifications.DidSaveTextDocument =>
      val msg = "LanguageServer: DidSaveTextDocument received"
      log.info(msg)
      sender() ! NotificationReceived.DidSaveTextDocument

    case Notifications.DidCloseTextDocument =>
      val msg = "LanguageServer: DidCloseTextDocument received"
      log.info(msg)
      sender() ! NotificationReceived.DidCloseTextDocument
  }
}
object LanguageServer {
  def props(context: ExecutionContext): Props =
    Props(new LanguageServer(context))
}
