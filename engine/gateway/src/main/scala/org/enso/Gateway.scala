package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import org.enso.gateway.protocol.response.Result.{
  ApplyWorkspaceEditResult,
  InitializeResult,
  NullResult,
  WillSaveTextDocumentWaitUntilResult
}
import org.enso.gateway.protocol.response.result.servercapabilities.TextDocumentSync
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
    case Requests.Initialize(id, params) =>
      val msg = "Gateway: Initialize received"
      log.info(msg)
      languageServer ! languageserver.Requests.Initialize(
        id.toLsModel,
        params
          .flatMap(
            _.capabilities.textDocument
              .flatMap(_.synchronization.flatMap(_.willSaveWaitUntil))
          )
          .getOrElse(false),
        sender()
      )

    case languageserver.RequestReceived.Initialize(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Initialize received"
      log.info(msg)
      replyTo ! Response.result(
        id = Some(Id.fromLsModel(id)),
        result = InitializeResult(
          capabilities = ServerCapabilities(
            textDocumentSync = Some(
              TextDocumentSync.WillSaveWaitUntil(
                willSaveWaitUntil = true
              )
            )
          ),
          serverInfo = Some(serverInfo)
        )
      )

    case Requests.Shutdown(id, _) =>
      val msg = "Gateway: Shutdown received"
      log.info(msg)
      languageServer ! languageserver.Requests.Shutdown(id.toLsModel, sender())

    case languageserver.RequestReceived.Shutdown(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Shutdown received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsModel(id)),
        result = NullResult
      )

    case Requests.ApplyWorkspaceEdit(id, _) =>
      val msg = "Gateway: ApplyWorkspaceEdit received"
      log.info(msg)
      languageServer ! languageserver.Requests
        .ApplyWorkspaceEdit(id.toLsModel, sender())

    case languageserver.RequestReceived.ApplyWorkspaceEdit(id, replyTo) =>
      val msg = "Gateway: RequestReceived.ApplyWorkspaceEdit received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsModel(id)),
        result = ApplyWorkspaceEditResult(applied = false)
      )

    case Requests.WillSaveTextDocumentWaitUntil(id, _) =>
      val msg = "Gateway: WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      languageServer ! languageserver.Requests
        .WillSaveTextDocumentWaitUntil(id.toLsModel, sender())

    case languageserver.RequestReceived
          .WillSaveTextDocumentWaitUntil(id, replyTo) =>
      val msg =
        "Gateway: RequestReceived.WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsModel(id)),
        result = WillSaveTextDocumentWaitUntilResult()
      )

    case Notifications.Initialized(_) =>
      val msg = "Gateway: Initialized received"
      log.info(msg)
      languageServer ! languageserver.Notifications.Initialized

    case languageserver.NotificationReceived.Initialized =>
      val msg = "Gateway: NotificationReceived.Initialized received"
      log.info(msg)

    case Notifications.Exit(_) =>
      val msg = "Gateway: Exit received"
      log.info(msg)
      languageServer ! languageserver.Notifications.Exit

    case languageserver.NotificationReceived.Exit =>
      val msg = "Gateway: NotificationReceived.Exit received"
      log.info(msg)

    case Notifications.DidOpenTextDocument(_) =>
      val msg = "Gateway: DidOpenTextDocument received"
      log.info(msg)
      languageServer ! languageserver.Notifications.DidOpenTextDocument

    case languageserver.NotificationReceived.DidOpenTextDocument =>
      val msg = "Gateway: NotificationReceived.DidOpenTextDocument received"
      log.info(msg)

    case Notifications.DidChangeTextDocument(_) =>
      val msg = "Gateway: DidChangeTextDocument received"
      log.info(msg)
      languageServer ! languageserver.Notifications.DidChangeTextDocument

    case languageserver.NotificationReceived.DidChangeTextDocument =>
      val msg = "Gateway: NotificationReceived.DidChangeTextDocument received"
      log.info(msg)

    case Notifications.DidSaveTextDocument(_) =>
      val msg = "Gateway: DidSaveTextDocument received"
      log.info(msg)
      languageServer ! languageserver.Notifications.DidSaveTextDocument

    case languageserver.NotificationReceived.DidSaveTextDocument =>
      val msg = "Gateway: NotificationReceived.DidSaveTextDocument received"
      log.info(msg)

    case Notifications.DidCloseTextDocument(_) =>
      val msg = "Gateway: DidCloseTextDocument received"
      log.info(msg)
      languageServer ! languageserver.Notifications.DidCloseTextDocument

    case languageserver.NotificationReceived.DidCloseTextDocument =>
      val msg = "Gateway: NotificationReceived.DidCloseTextDocument received"
      log.info(msg)

    case requestOrNotification =>
      val err = "Gateway: unimplemented request or notification: " +
        requestOrNotification
      log.error(err)
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
