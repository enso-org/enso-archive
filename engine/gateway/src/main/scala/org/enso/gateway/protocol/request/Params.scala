package org.enso.gateway.protocol.request

import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import cats.syntax.functor._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
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
    Decoder[DidCloseTextDocumentParams].widen,
    Decoder[InitializeParams].widen
  ).reduceLeft(_ or _)

  implicit val paramsEncoder: Encoder[Params] = Encoder.instance {
    case params: Array                               => params.asJson
    case params: VoidParams                          => params.asJson
    case params: InitializeParams                    => params.asJson
    case params: ApplyWorkspaceEditParams            => params.asJson
    case params: DidOpenTextDocumentParams           => params.asJson
    case params: DidChangeTextDocumentParams         => params.asJson
    case params: WillSaveTextDocumentWaitUntilParams => params.asJson
    case params: DidSaveTextDocumentParams           => params.asJson
    case params: DidCloseTextDocumentParams          => params.asJson
    case params: InitializeParams                    => params.asJson
  }

  type DocumentUri = String

  /** Array params. */
  case class Array(value: Seq[Option[Param]]) extends Params

  object Array {
    implicit val paramsArrayDecoder: Decoder[Array] =
      deriveUnwrappedDecoder
    implicit val paramsArrayEncoder: Encoder[Array] =
      deriveUnwrappedEncoder
  }

  /** Void params.
    *
    * Params of [[org.enso.gateway.protocol.Requests.Shutdown]],
    * [[org.enso.gateway.protocol.Notifications.Initialized]],
    * [[org.enso.gateway.protocol.Notifications.Exit]].
    */
  case class VoidParams() extends Params
  object VoidParams {
    implicit val voidParamsDecoder: Decoder[VoidParams] =
      deriveDecoder
    implicit val voidParamsEncoder: Encoder[VoidParams] =
      deriveEncoder
  }

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
    implicit val initializeParamsEncoder: Encoder[InitializeParams] =
      deriveEncoder
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
    label: Option[String] = None,
    edit: WorkspaceEdit
  ) extends Params
  object ApplyWorkspaceEditParams {
    implicit val applyWorkspaceEditParamsDecoder
      : Decoder[ApplyWorkspaceEditParams] = deriveDecoder
    implicit val applyWorkspaceEditParamsEncoder
      : Encoder[ApplyWorkspaceEditParams] = deriveEncoder
  }

  //textDocument/didOpen
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidOpenTextDocument]].
    */
  case class DidOpenTextDocumentParams(textDocument: TextDocumentItem)
      extends Params
  object DidOpenTextDocumentParams {
    implicit val didOpenTextDocumentParamsDecoder
      : Decoder[DidOpenTextDocumentParams] = deriveDecoder
    implicit val didOpenTextDocumentParamsEncoder
      : Encoder[DidOpenTextDocumentParams] = deriveEncoder
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
      : Decoder[DidChangeTextDocumentParams] = deriveDecoder
    implicit val didChangeTextDocumentParamsEncoder
      : Encoder[DidChangeTextDocumentParams] = deriveEncoder
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
    implicit val willSaveTextDocumentWaitUntilParamsEncoder
      : Encoder[WillSaveTextDocumentWaitUntilParams] =
      deriveEncoder
  }

  //textDocument/didSave
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidSaveTextDocument]].
    */
  case class DidSaveTextDocumentParams(
    textDocument: TextDocumentIdentifier,
    text: Option[String] = None
  ) extends Params
  object DidSaveTextDocumentParams {
    implicit val didSaveTextDocumentParamsDecoder
      : Decoder[DidSaveTextDocumentParams] = deriveDecoder
    implicit val didSaveTextDocumentParamsEncoder
      : Encoder[DidSaveTextDocumentParams] = deriveEncoder
  }

  //textDocument/didClose
  /** Params of the notification
    * [[org.enso.gateway.protocol.Notifications.DidCloseTextDocument]].
    */
  case class DidCloseTextDocumentParams(textDocument: TextDocumentIdentifier)
      extends Params
  object DidCloseTextDocumentParams {
    implicit val didCloseTextDocumentParamsDecoder
      : Decoder[DidCloseTextDocumentParams] = deriveDecoder
    implicit val didCloseTextDocumentParamsEncoder
      : Encoder[DidCloseTextDocumentParams] = deriveEncoder
  }
}
