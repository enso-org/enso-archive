package org.enso.gateway.protocol

import io.circe.CursorOp.DownField
import io.circe.{Decoder, DecodingFailure}
import org.enso.gateway.protocol.request.Params.{
  ApplyWorkspaceEditParams,
  DidChangeTextDocumentParams,
  DidCloseTextDocumentParams,
  DidOpenTextDocumentParams,
  DidSaveTextDocumentParams,
  InitializeParams,
  VoidParams,
  WillSaveTextDocumentWaitUntilParams
}

/**
  * Helper object for decoding [[RequestOrNotification]].
  */
object RequestOrNotificationDecoder {

  /**
    * Circe decoder for requests and notifications.
    */
  val instance: Decoder[RequestOrNotification] =
    cursor => {
      val methodCursor = cursor.downField(Notification.methodField)
      Decoder[String]
        .tryDecode(methodCursor)
        .flatMap(selectRequestOrNotificationDecoder(_).apply(cursor))
    }

  /**
    *
    * @param method Name of method.
    * @return Circe failure if method is unknown.
    */
  def unknownMethodFailure(method: String): DecodingFailure =
    DecodingFailure(
      unknownMethodMessage(method),
      List(DownField(Notification.methodField))
    )

  /**
    * @param method Name of method, which is discriminator
    * @return Circe decoder for requests or notifications
    */
  private def selectRequestOrNotificationDecoder(
    method: String
  ): Decoder[_ <: RequestOrNotification] =
    method match {
      // All requests
      case Requests.Initialize.method =>
        Decoder[Request[InitializeParams]]
      case Requests.Shutdown.method =>
        Decoder[Request[VoidParams]]
      case Requests.ApplyWorkspaceEdit.method =>
        Decoder[Request[ApplyWorkspaceEditParams]]
      case Requests.WillSaveTextDocumentWaitUntil.method =>
        Decoder[Request[WillSaveTextDocumentWaitUntilParams]]

      // All notifications
      case Notifications.Initialized.method | Notifications.Exit.method =>
        Decoder[Notification[VoidParams]]
      case Notifications.DidOpenTextDocument.method =>
        Decoder[Notification[DidOpenTextDocumentParams]]
      case Notifications.DidChangeTextDocument.method =>
        Decoder[Notification[DidChangeTextDocumentParams]]
      case Notifications.DidSaveTextDocument.method =>
        Decoder[Notification[DidSaveTextDocumentParams]]
      case Notifications.DidCloseTextDocument.method =>
        Decoder[Notification[DidCloseTextDocumentParams]]

      case m =>
        Decoder.failed(
          unknownMethodFailure(m)
        )
    }

  private def unknownMethodMessage(method: String) = s"Unknown method $method"
}
