package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.polyglot.ExecutionContext

/** The language server component wraps the runtime itself, and uses the APIs
  * provided by the interpreter and the compiler to service the requests sent
  * to the Enso Engine.
  */
class LanguageServer(context: ExecutionContext)
    extends Actor
    with ActorLogging {
  override def receive: Receive = {
    case LanguageServer.Requests.Initialize(id, actorRef) =>
      val msg = "LanguageServer: Initialize received"
      log.info(msg)
      sender() ! LanguageServer.RequestReceived.Initialize(id, actorRef)

    case LanguageServer.Requests.Shutdown(id, actorRef) =>
      val msg = "LanguageServer: Shutdown received"
      log.info(msg)
      sender() ! LanguageServer.RequestReceived.Shutdown(id, actorRef)

    case LanguageServer.Requests.ApplyWorkspaceEdit(id, actorRef) =>
      val msg = "LanguageServer: ApplyWorkspaceEdit received"
      log.info(msg)
      sender() ! LanguageServer.RequestReceived.ApplyWorkspaceEdit(id, actorRef)

    case LanguageServer.Requests.WillSaveTextDocumentWaitUntil(id, actorRef) =>
      val msg = "LanguageServer: WillSaveTextDocumentWaitUntilEdit received"
      log.info(msg)
      sender() ! LanguageServer.RequestReceived
        .WillSaveTextDocumentWaitUntil(id, actorRef)

    case LanguageServer.Notifications.Initialized =>
      val msg = "LanguageServer: Initialized received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.Initialized

    case LanguageServer.Notifications.Exit =>
      val msg = "LanguageServer: Exit received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.Exit

    case LanguageServer.Notifications.DidOpenTextDocument =>
      val msg = "LanguageServer: DidOpenTextDocument received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.DidOpenTextDocument

    case LanguageServer.Notifications.DidChangeTextDocument =>
      val msg = "LanguageServer: DidChangeTextDocument received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.DidChangeTextDocument

    case LanguageServer.Notifications.DidSaveTextDocument =>
      val msg = "LanguageServer: DidSaveTextDocument received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.DidSaveTextDocument

    case LanguageServer.Notifications.DidCloseTextDocument =>
      val msg = "LanguageServer: DidCloseTextDocument received"
      log.info(msg)
      sender() ! LanguageServer.NotificationReceived.DidCloseTextDocument
  }
}
object LanguageServer {

  object Requests {

    /** Akka message sent by Gateway received LSP request `initialize`. */
    case class Initialize(id: Int, replyTo: ActorRef)

    /** Language server response to [[]]. */
    case class Shutdown(id: Int, replyTo: ActorRef)

    /** Language server response to [[]]. */
    case class ApplyWorkspaceEdit(id: Int, replyTo: ActorRef)

    /** Language server response to [[]]. */
    case class WillSaveTextDocumentWaitUntil(id: Int, replyTo: ActorRef)

  }

  object RequestReceived {

    /** Language server response to [[Initialize]]. */
    case class Initialize(id: Int, replyTo: ActorRef)

    /** */
    case class Shutdown(id: Int, replyTo: ActorRef)

    /** */
    case class ApplyWorkspaceEdit(id: Int, replyTo: ActorRef)

    /** */
    case class WillSaveTextDocumentWaitUntil(id: Int, replyTo: ActorRef)

  }

  object Notifications {

    /** Akka message sent by Gateway received LSP notification `initialized`. */
    case object Initialized

    /** */
    case object Exit

    /** */
    case object DidOpenTextDocument

    /** */
    case object DidChangeTextDocument

    /** */
    case object DidSaveTextDocument

    /** */
    case object DidCloseTextDocument

  }

  object NotificationReceived {

    /** Language server response to [[Initialized]]. */
    case object Initialized

    /** */
    case object Exit

    /** */
    case object DidOpenTextDocument

    /** */
    case object DidChangeTextDocument

    /** */
    case object DidSaveTextDocument

    /** */
    case object DidCloseTextDocument

  }

  def props(context: ExecutionContext): Props =
    Props(new LanguageServer(context))
}
