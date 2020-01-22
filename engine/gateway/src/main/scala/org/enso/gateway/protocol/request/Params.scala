package org.enso.gateway.protocol.request

import io.circe.generic.semiauto._
import io.circe.Decoder
import cats.syntax.functor._
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import org.enso.gateway.protocol.request.Param.{
  ClientCapabilities,
  ClientInfo,
  InitializationOptions,
  TextDocumentContentChangeEvent,
  TextDocumentIdentifier,
  TextDocumentItem,
  TextDocumentSaveReason,
  Trace,
  VersionedTextDocumentIdentifier,
  WorkspaceEdit,
  WorkspaceFolder
}

/** Params of [[org.enso.gateway.protocol.RequestOrNotification]].
  * Can be array or JSON object.
  */
sealed trait Params
object Params {
  implicit val paramsDecoder: Decoder[Params] = List[Decoder[Params]](
    Decoder[Array].widen,
    Decoder[VoidParams].widen,
    Decoder[InitializeParams].widen,
    Decoder[ApplyWorkspaceEditParams].widen,
    Decoder[DidOpenTextDocumentParams].widen,
    Decoder[DidChangeTextDocumentParams].widen,
    Decoder[WillSaveTextDocumentWaitUntilParams].widen,
    Decoder[DidSaveTextDocumentParams].widen,
    Decoder[DidCloseTextDocumentParams].widen
  ).reduceLeft(_ or _)

  type DocumentUri = String

  /** Array params. */
  case class Array(value: Seq[Option[Param]]) extends Params
  object Array {
    implicit val paramsArrayDecoder: Decoder[Array] =
      deriveUnwrappedDecoder
  }

  //initialized, shutdown, exit
  /** Void params. */
  case class VoidParams() extends Params
  object VoidParams {
    implicit val voidParamsDecoder: Decoder[VoidParams] =
      deriveDecoder
  }

  //initialize
  /** Params of the request [[org.enso.gateway.protocol.Requests.Initialize]].
    */
  case class InitializeParams(
    processId: Option[Int]         = None,
    clientInfo: Option[ClientInfo] = None,
    // Note [rootPath deprecated]
    rootPath: Option[String]                             = None,
    rootUri: Option[DocumentUri]                         = None,
    initializationOptions: Option[InitializationOptions] = None,
    capabilities: ClientCapabilities,
    trace: Option[Trace]                           = None,
    workspaceFolders: Option[Seq[WorkspaceFolder]] = None
  ) extends Params
  object InitializeParams {
    implicit val initializeParamsDecoder: Decoder[InitializeParams] =
      deriveDecoder
  }

  /* Note [rootPath deprecated]
   * ~~~~~~~~~~~~~~~~~~~~~~~~~~
   * `rootPath` is deprecated: use `rootUri`, LSP Spec.
   */

  //workspace/applyEdit
  /** Params of the request
    * [[org.enso.gateway.protocol.Requests.ApplyWorkspaceEdit]].
    */
  case class ApplyWorkspaceEditParams(
    label: Option[String],
    edit: WorkspaceEdit
  ) extends Params
  object ApplyWorkspaceEditParams {
    implicit val applyWorkspaceEditParamsDecoder
      : Decoder[ApplyWorkspaceEditParams] =
      deriveDecoder
  }

  //textDocument/didOpen
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidOpenTextDocument]].
    */
  case class DidOpenTextDocumentParams(textDocument: TextDocumentItem)
      extends Params
  object DidOpenTextDocumentParams {
    implicit val didOpenTextDocumentParamsDecoder
      : Decoder[DidOpenTextDocumentParams] =
      deriveDecoder
  }

  //textDocument/didChange
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidChangeTextDocument]].
    */
  case class DidChangeTextDocumentParams(
    textDocument: VersionedTextDocumentIdentifier,
    contentChanges: Seq[TextDocumentContentChangeEvent]
  ) extends Params
  object DidChangeTextDocumentParams {
    implicit val didChangeTextDocumentParamsDecoder
      : Decoder[DidChangeTextDocumentParams] =
      deriveDecoder
  }

  //textDocument/willSaveWaitUntil
  /** Params of the request
    * [[org.enso.gateway.protocol.Requests.WillSaveTextDocumentWaitUntil]].
    */
  case class WillSaveTextDocumentWaitUntilParams(
    textDocument: TextDocumentIdentifier,
    reason: TextDocumentSaveReason
  ) extends Params
  object WillSaveTextDocumentWaitUntilParams {
    implicit val willSaveTextDocumentWaitUntilParamsDecoder
      : Decoder[WillSaveTextDocumentWaitUntilParams] =
      deriveDecoder
  }

  //textDocument/didSave
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidSaveTextDocument]].
    */
  case class DidSaveTextDocumentParams(
    textDocument: TextDocumentIdentifier,
    text: Option[String]
  ) extends Params
  object DidSaveTextDocumentParams {
    implicit val didSaveTextDocumentParamsDecoder
      : Decoder[DidSaveTextDocumentParams] =
      deriveDecoder
  }

  //textDocument/didClose
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidCloseTextDocument]].
    */
  case class DidCloseTextDocumentParams(textDocument: TextDocumentIdentifier)
      extends Params
  object DidCloseTextDocumentParams {
    implicit val didCloseTextDocumentParamsDecoder
      : Decoder[DidCloseTextDocumentParams] =
      deriveDecoder
  }
}
