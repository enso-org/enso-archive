package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import org.enso.gateway.protocol.response.Result.{
  ApplyWorkspaceEditResult,
  InitializeResult,
  NullResult,
  WillSaveTextDocumentWaitUntilResult
}
import org.enso.gateway.protocol.{Id, Notifications, Requests, Response}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}

/** The gateway component talks directly to clients using protocol messages,
  * and then handles these messages by talking to the language server.
  *
  * @param languageServer [[ActorRef]] of [[LanguageServer]] actor.
  */
class Gateway(languageServer: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Requests.Initialize(Id.Number(id), _) =>
      val msg = "Gateway: Initialize received"
      log.info(msg)
      languageServer ! LanguageServer.Requests.Initialize(id, sender())

    case LanguageServer.RequestReceived.Initialize(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Initialize received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.Number(id)),
        result = InitializeResult(ServerCapabilities(), Some(serverInfo))
      )

    case Requests.Shutdown(Id.Number(id), _) =>
      val msg = "Gateway: Shutdown received"
      log.info(msg)
      languageServer ! LanguageServer.Requests.Shutdown(id, sender())

    case LanguageServer.RequestReceived.Shutdown(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Shutdown received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.Number(id)),
        result = NullResult
      )

    case Requests.ApplyWorkspaceEdit(Id.Number(id), _) =>
      val msg = "Gateway: ApplyWorkspaceEdit received"
      log.info(msg)
      languageServer ! LanguageServer.Requests.ApplyWorkspaceEdit(id, sender())

    case LanguageServer.RequestReceived.ApplyWorkspaceEdit(id, replyTo) =>
      val msg = "Gateway: RequestReceived.ApplyWorkspaceEdit received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.Number(id)),
        result = ApplyWorkspaceEditResult(applied = false)
      )

    case Requests.WillSaveTextDocumentWaitUntil(Id.Number(id), _) =>
      val msg = "Gateway: WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      languageServer ! LanguageServer.Requests
        .WillSaveTextDocumentWaitUntil(id, sender())

    case LanguageServer.RequestReceived
          .WillSaveTextDocumentWaitUntil(id, replyTo) =>
      val msg =
        "Gateway: RequestReceived.WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.Number(id)),
        result = WillSaveTextDocumentWaitUntilResult()
      )

    case Notifications.Initialized(_) =>
      val msg = "Gateway: Initialized received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.Initialized

    case LanguageServer.NotificationReceived.Initialized =>
      val msg = "Gateway: NotificationReceived.Initialized received"
      log.info(msg)

    case Notifications.Exit(_) =>
      val msg = "Gateway: Exit received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.Exit

    case LanguageServer.NotificationReceived.Exit =>
      val msg = "Gateway: NotificationReceived.Exit received"
      log.info(msg)

    case Notifications.DidOpenTextDocument(_) =>
      val msg = "Gateway: DidOpenTextDocument received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.DidOpenTextDocument

    case LanguageServer.NotificationReceived.DidOpenTextDocument =>
      val msg = "Gateway: NotificationReceived.DidOpenTextDocument received"
      log.info(msg)

    case Notifications.DidChangeTextDocument(_) =>
      val msg = "Gateway: DidChangeTextDocument received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.DidChangeTextDocument

    case LanguageServer.NotificationReceived.DidChangeTextDocument =>
      val msg = "Gateway: NotificationReceived.DidChangeTextDocument received"
      log.info(msg)

    case Notifications.DidSaveTextDocument(_) =>
      val msg = "Gateway: DidSaveTextDocument received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.DidSaveTextDocument

    case LanguageServer.NotificationReceived.DidSaveTextDocument =>
      val msg = "Gateway: NotificationReceived.DidSaveTextDocument received"
      log.info(msg)

    case Notifications.DidCloseTextDocument(_) =>
      val msg = "Gateway: DidCloseTextDocument received"
      log.info(msg)
      languageServer ! LanguageServer.Notifications.DidCloseTextDocument

    case LanguageServer.NotificationReceived.DidCloseTextDocument =>
      val msg = "Gateway: NotificationReceived.DidCloseTextDocument received"
      log.info(msg)

    case requestOrNotification =>
      val err =
        s"unimplemented request or notification: $requestOrNotification"
      throw new Exception(err)
  }

  private val serverInfo: ServerInfo = {
    val gatewayPath               = "gateway"
    val languageServerPath        = "languageServer"
    val languageServerNamePath    = "name"
    val languageServerVersionPath = "version"
    val gatewayConfig             = ConfigFactory.load.getConfig(gatewayPath)
    val languageServerConfig      = gatewayConfig.getConfig(languageServerPath)
    val name                      = languageServerConfig.getString(languageServerNamePath)
    val version                   = languageServerConfig.getString(languageServerVersionPath)
    ServerInfo(name, Some(version))
  }
}
object Gateway {
  def props(languageServer: ActorRef): Props =
    Props(new Gateway(languageServer))
}
