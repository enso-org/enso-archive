package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.enso.gateway.protocol.response.Result.{
  InitializeResult,
  NullResult,
  WillSaveTextDocumentWaitUntilResult
}
import org.enso.gateway.protocol.response.result.servercapabilities.TextDocumentSync
import org.enso.gateway.protocol.{
  Id,
  JsonRpcController,
  Notifications,
  Requests,
  RequestsToClient,
  Response,
  ResponsesFromClient
}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import org.enso.gateway.protocol.response.ResponseError
import org.enso.gateway.protocol.response.error.Data
import org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync.{
  TextDocumentSyncDidSave,
  TextDocumentSyncKind
}
import org.enso.{languageserver => ls}

/** The Gateway component of Enso Engine.
  *
  * Talks directly to clients using protocol messages, and then handles these
  * messages by talking to the language server.
  *
  * @param languageServer [[ActorRef]] of [[LanguageServer]] actor.
  */
class Gateway(languageServer: ActorRef) extends Actor with ActorLogging {
  var jsonRpcController: JsonRpcController = _

  override def receive: Receive = {
    case Gateway.SetJsonRpcController(jsonRpcController) =>
      val msg = "Gateway: SetJsonRpcController received"
      log.info(msg)
      this.jsonRpcController = jsonRpcController

    case Requests.Initialize(id, params) =>
      val msg = "Gateway: Initialize received"
      log.info(msg)
      languageServer ! ls.Request.Initialize(
        id = id.toLsModel,
        ls.model.ClientCapabilities(
          dynamicRegistration = params
            .flatMap(
              _.capabilities.textDocument
                .flatMap(_.synchronization.flatMap(_.dynamicRegistration))
            )
            .getOrElse(false),
          willSaveWaitUntil = params
            .flatMap(
              _.capabilities.textDocument
                .flatMap(_.synchronization.flatMap(_.willSaveWaitUntil))
            )
            .getOrElse(false),
          didSave = params
            .flatMap(
              _.capabilities.textDocument
                .flatMap(_.synchronization.flatMap(_.didSave))
            )
            .getOrElse(false)
        ),
        replyTo = sender()
      )

    case ls.Response.Initialize(id, serverInfo, replyTo) =>
      val msg = "Gateway: Response.Initialize received"
      log.info(msg)
      replyTo ! Response.result(
        id = Some(Id.fromLsModel(id)),
        result = InitializeResult(
          capabilities = ServerCapabilities(
            textDocumentSync = Some(
              TextDocumentSync.TextDocumentSyncOptions(
                openClose         = Some(true),
                change            = Some(TextDocumentSyncKind.Full),
                willSaveWaitUntil = Some(true),
                didSave           = Some(TextDocumentSyncDidSave.Bool(true))
              )
            )
          ),
          serverInfo = Some(ServerInfo.fromLsModel(serverInfo))
        )
      )

    case Requests.Shutdown(id, _) =>
      val msg = "Gateway: Shutdown received"
      log.info(msg)
      languageServer ! ls.Request.Shutdown(id.toLsModel, sender())

    case ls.Response.Shutdown(id, replyTo) =>
      val msg = "Gateway: Response.Shutdown received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsModel(id)),
        result = NullResult
      )

    case ls.RequestToClient.ApplyWorkspaceEdit(id) =>
      val msg = "Gateway: RequestToClient.ApplyWorkspaceEdit received"
      log.info(msg)
      jsonRpcController.handleRequestOrNotificationToClient(
        RequestsToClient.ApplyWorkspaceEdit(Id.fromLsModel(id))
      )

    case ResponsesFromClient.ApplyWorkspaceEdit(id, _) =>
      val msg = "Gateway: ApplyWorkspaceEdit received"
      log.info(msg)
      languageServer ! ls.ResponseFromClient
        .ApplyWorkspaceEdit(id.toLsModel)

    case Requests.WillSaveTextDocumentWaitUntil(id, _) =>
      val msg = "Gateway: WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      languageServer ! ls.Request
        .WillSaveTextDocumentWaitUntil(id.toLsModel, sender())

    case ls.Response
          .WillSaveTextDocumentWaitUntil(id, replyTo) =>
      val msg =
        "Gateway: Response.WillSaveTextDocumentWaitUntil received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsModel(id)),
        result = WillSaveTextDocumentWaitUntilResult()
      )

    case Notifications.Initialized(_) =>
      val msg = "Gateway: Initialized received"
      log.info(msg)
      languageServer ! ls.Notification.Initialized

    case Notifications.Exit(_) =>
      val msg = "Gateway: Exit received"
      log.info(msg)
      languageServer ! ls.Notification.Exit

    case Notifications.DidOpenTextDocument(_) =>
      val msg = "Gateway: DidOpenTextDocument received"
      log.info(msg)
      languageServer ! ls.Notification.DidOpenTextDocument

    case Notifications.DidChangeTextDocument(_) =>
      val msg = "Gateway: DidChangeTextDocument received"
      log.info(msg)
      languageServer ! ls.Notification.DidChangeTextDocument

    case Notifications.DidSaveTextDocument(_) =>
      val msg = "Gateway: DidSaveTextDocument received"
      log.info(msg)
      languageServer ! ls.Notification.DidSaveTextDocument

    case Notifications.DidCloseTextDocument(_) =>
      val msg = "Gateway: DidCloseTextDocument received"
      log.info(msg)
      languageServer ! ls.Notification.DidCloseTextDocument

    case ls.ErrorResponse.InvalidRequest(id, message, replyTo) =>
      val msg = "Gateway: ErrorResponse.InvalidRequest received"
      log.error(msg)
      replyTo ! Response.error(
        id = Some(Id.fromLsModel(id)),
        error = ResponseError.invalidRequest(
          data = Some(Data.Text(message))
        )
      )

    case ls.ErrorResponse.ServerNotInitialized(id, message, replyTo) =>
      val msg = "Gateway: ErrorResponse.InvalidRequest received"
      log.error(msg)
      replyTo ! Response.error(
        id = Some(Id.fromLsModel(id)),
        error = ResponseError.serverNotInitialized(
          data = Some(Data.Text(message))
        )
      )

    case message =>
      val err = s"Gateway: unexpected incoming message: $message"
      log.error(err)
  }
}
object Gateway {
  def props(
    languageServer: ActorRef
  ): Props =
    Props(new Gateway(languageServer))

  case class SetJsonRpcController(jsonRpcController: JsonRpcController)

}
