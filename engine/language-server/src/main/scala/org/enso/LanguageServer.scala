package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import org.enso.languageserver.model.ServerInfo
import org.enso.languageserver.{
  ErrorResponse,
  Notification,
  Request,
  RequestToClient,
  Response,
  ResponseFromClient,
  SendRequestToClient,
  SetGateway
}
import org.enso.polyglot.ExecutionContext

/** The Language Server component of Enso Engine.
  *
  * Wraps the runtime itself, and uses the APIs provided by the interpreter
  * and the compiler to service the requests sent to the Enso Engine.
  *
  * @param executionContext Polyglot Execution context.
  */
class LanguageServer(
  executionContext: ExecutionContext
) extends Actor
    with ActorLogging {

  import context._

  private var gateway: ActorRef = _

  override def receive: Receive = {
    case SetGateway(actorRef) =>
      val msg =
        s"LanguageServer: SetGateway received"
      log.info(msg)
      gateway = actorRef

    case Notification.Exit =>
      exit()

    case notification: Notification =>
      val msg =
        s"LanguageServer: $notification received before Initialize and dropped"
      log.info(msg)

    case Request.Initialize(id, _, actorRef) =>
      val msg = "LanguageServer: Initialize received"
      log.info(msg)
      sender() ! Response.Initialize(
        id,
        LanguageServer.serverInfo,
        actorRef
      )
      become(afterInitialize)

    case request: Request =>
      val msg = s"LanguageServer: $request received before Initialize"
      log.error(msg)
      sender() ! ErrorResponse.ServerNotInitialized(
        request.id,
        msg,
        request.replyTo
      )

    case message => default(message)
  }

  def afterInitialize: Receive = {
    case Notification.Initialized =>
      val msg = "LanguageServer: Initialized received"
      log.info(msg)
      become(afterInitialized)

    case request: Request =>
      val msg =
        s"LanguageServer: $request received before Initialized"
      log.error(msg)
      sender() ! ErrorResponse.InvalidRequest(request.id, msg, request.replyTo)

    case notification: Notification =>
      val msg =
        s"LanguageServer: $notification received before Initialized and dropped"
      log.error(msg)

    case message => default(message)
  }

  def afterInitialized: Receive = {
    case Request.Initialize(id, _, actorRef) =>
      val msg =
        s"LanguageServer: Initialize received after Initialized"
      log.error(msg)
      sender() ! ErrorResponse.InvalidRequest(id, msg, actorRef)

    case Notification.Initialized =>
      val msg =
        s"LanguageServer: Initialized received after Initialized and dropped"
      log.error(msg)

    case Request.Shutdown(id, actorRef) =>
      val msg = "LanguageServer: Shutdown received"
      log.info(msg)
      sender() ! Response.Shutdown(id, actorRef)
      become(afterShutdown)

    case Request.WillSaveTextDocumentWaitUntil(id, actorRef) =>
      val msg = "LanguageServer: WillSaveTextDocumentWaitUntilEdit received"
      log.info(msg)
      sender() ! Response.WillSaveTextDocumentWaitUntil(id, actorRef)

    case Notification.DidOpenTextDocument =>
      val msg = "LanguageServer: DidOpenTextDocument received"
      log.info(msg)

    case Notification.DidChangeTextDocument =>
      val msg = "LanguageServer: DidChangeTextDocument received"
      log.info(msg)

    case Notification.DidSaveTextDocument =>
      val msg = "LanguageServer: DidSaveTextDocument received"
      log.info(msg)

    case Notification.DidCloseTextDocument =>
      val msg = "LanguageServer: DidCloseTextDocument received"
      log.info(msg)

    case SendRequestToClient.SendApplyWorkspaceEdit(id) =>
      val msg = "LanguageServer: SendApplyWorkspaceEdit received"
      log.info(msg)
      gateway ! RequestToClient.ApplyWorkspaceEdit(id)

    case ResponseFromClient.ApplyWorkspaceEdit(_) =>
      val msg = "LanguageServer: ResponseFromClient.ApplyWorkspaceEdit received"
      log.info(msg)

    case message => default(message)
  }

  def afterShutdown: Receive = {
    case Notification.Exit =>
      exit()

    case request: Request =>
      val msg =
        s"LanguageServer: $request received after Shutdown"
      log.error(msg)
      sender() ! ErrorResponse.InvalidRequest(request.id, msg, request.replyTo)

    case notification: Notification =>
      val msg =
        s"LanguageServer: $notification received after Shutdown and dropped"
      log.error(msg)

    case message => default(message)
  }

  private def default(message: Any): Unit = {
    val msg = "LanguageServer: unexpected incoming message " +
      message
    log.error(msg)
  }

  private def exit(): Unit = {
    val msg = "LanguageServer: Exit received"
    log.info(msg)
    become(receive)
  }
}
object LanguageServer {
  def props(context: ExecutionContext): Props =
    Props(new LanguageServer(context))

  private val serverInfo: ServerInfo = {
    val path        = "languageServer"
    val namePath    = "name"
    val versionPath = "version"
    val config      = ConfigFactory.load.getConfig(path)
    val name        = config.getString(namePath)
    val version     = config.getString(versionPath)
    ServerInfo(name, version)
  }
}
