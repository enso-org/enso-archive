package org.enso.gateway.protocol.request

import io.circe.generic.semiauto._
import io.circe.Decoder
import cats.syntax.functor._
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import org.enso.gateway.protocol.request.Param.{
  ClientCapabilities,
  ClientInfo,
  InitializationOptions,
  TextDocumentItem,
  Trace,
  WorkspaceFolder
}

/** Params of [[org.enso.gateway.protocol.RequestOrNotification]].
  * Can be array or JSON object.
  */
sealed trait Params
object Params {
  implicit val paramsDecoder: Decoder[Params] = List[Decoder[Params]](
    Decoder[InitializeParams].widen,
    Decoder[VoidParams].widen,
    Decoder[Array].widen
  ).reduceLeft(_ or _)

  type DocumentUri = String

  /** Params of the request
    * [[org.enso.gateway.protocol.Requests.Initialize]].
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

  /**  */
  case class DidOpenTextDocumentParams(textDocument: TextDocumentItem)
      extends Params
  object DidOpenTextDocumentParams {
    implicit val didOpenTextDocumentParamsDecoder
      : Decoder[DidOpenTextDocumentParams] =
      deriveDecoder
  }

  /**  */
  case class ApplyWorkspaceEditParams() extends Params
  object ApplyWorkspaceEditParams {
    implicit val applyWorkspaceEditParamsDecoder
      : Decoder[ApplyWorkspaceEditParams] =
      deriveDecoder
  }

  /**  */
  case class WillSaveTextDocumentWaitUntilParams() extends Params
  object WillSaveTextDocumentWaitUntilParams {
    implicit val willSaveTextDocumentWaitUntilParamsDecoder
      : Decoder[WillSaveTextDocumentWaitUntilParams] =
      deriveDecoder
  }

  /**  */
  case class DidChangeTextDocumentParams() extends Params
  object DidChangeTextDocumentParams {
    implicit val didChangeTextDocumentParamsDecoder
      : Decoder[DidChangeTextDocumentParams] =
      deriveDecoder
  }

  /**  */
  case class DidSaveTextDocumentParams() extends Params
  object DidSaveTextDocumentParams {
    implicit val didSaveTextDocumentParamsDecoder
      : Decoder[DidSaveTextDocumentParams] =
      deriveDecoder
  }

  /**  */
  case class DidCloseTextDocumentParams() extends Params
  object DidCloseTextDocumentParams {
    implicit val didCloseTextDocumentParamsDecoder
      : Decoder[DidCloseTextDocumentParams] =
      deriveDecoder
  }

  /** Void params. */
  case class VoidParams() extends Params
  object VoidParams {
    implicit val voidParamsDecoder: Decoder[VoidParams] =
      deriveDecoder
  }

  /** Array params. */
  case class Array(value: Seq[Option[Param]]) extends Params
  object Array {
    implicit val paramsArrayDecoder: Decoder[Array] =
      deriveUnwrappedDecoder
  }
}
