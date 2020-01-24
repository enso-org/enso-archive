package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.extras.semiauto.{
  deriveEnumerationDecoder,
  deriveUnwrappedDecoder
}
import io.circe.generic.semiauto.deriveDecoder
import cats.syntax.functor._
import org.enso.gateway.protocol.{TextEdit, TextRange}
import org.enso.gateway.protocol.request.Params.DocumentUri

/** An element of [[Params.Array]]. */
sealed trait Param
object Param {
  implicit val paramDecoder: Decoder[Param] = List[Decoder[Param]](
    Decoder[Number].widen,
    Decoder[Bool].widen,
    Decoder[Array].widen,
    Decoder[Text].widen,
    Decoder[ClientInfo].widen,
    Decoder[ClientCapabilities].widen,
    Decoder[InitializationOptions].widen,
    Decoder[Trace].widen,
    Decoder[WorkspaceFolder].widen,
    Decoder[TextDocumentItem].widen,
    Decoder[WorkspaceEdit].widen,
    Decoder[TextDocumentIdentifier].widen,
    Decoder[TextDocumentSaveReason].widen,
    Decoder[TextDocumentContentChangeEvent].widen,
    Decoder[VersionedTextDocumentIdentifier].widen,
    Decoder[WorkspaceFolder].widen
  ).reduceLeft(_ or _)

  /** A string element. */
  case class Text(value: String) extends Param
  object Text {
    implicit val paramStringDecoder: Decoder[Text] = deriveUnwrappedDecoder
  }

  /** A number element. */
  case class Number(value: Int) extends Param
  object Number {
    implicit val paramNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /** A boolean element. */
  case class Bool(value: Boolean) extends Param
  object Bool {
    implicit val paramBooleanDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  /** An array element. */
  case class Array(value: Seq[Option[Param]]) extends Param
  object Array {
    implicit val paramArrayDecoder: Decoder[Array] =
      deriveUnwrappedDecoder
  }

  /** A param of the request [[org.enso.gateway.protocol.Requests.Initialize]].
    *
    * @see [[org.enso.gateway.protocol.request.Params.InitializeParams]].
    */
  case class InitializationOptions(value: String) extends Param
  object InitializationOptions {
    implicit val initializationOptionsDecoder: Decoder[InitializationOptions] =
      deriveUnwrappedDecoder
  }

  /** A param of the request [[org.enso.gateway.protocol.Requests.Initialize]]
    *
    * @see [[org.enso.gateway.protocol.request.Params.InitializeParams]].
    */
  case class ClientInfo(
    name: String,
    version: Option[String]
  ) extends Param
  object ClientInfo {
    implicit val clientInfoDecoder: Decoder[ClientInfo] = deriveDecoder
  }

  /** A param of the request [[org.enso.gateway.protocol.Requests.Initialize]].
    *
    * The initial trace setting.
    *
    * @see [[org.enso.gateway.protocol.request.Params.InitializeParams]].
    */
  sealed trait Trace extends Param
  object Trace {
    implicit val traceOffDecoder: Decoder[Trace] = deriveEnumerationDecoder

    case object off extends Trace

    case object messages extends Trace

    case object verbose extends Trace
  }

  /** A param of the request [[org.enso.gateway.protocol.Requests.Initialize]].
    *
    * @see [[org.enso.gateway.protocol.request.Params.InitializeParams]].
    */
  case class WorkspaceFolder(
    uri: DocumentUri,
    name: String
  ) extends Param
  object WorkspaceFolder {
    implicit val workspaceFolderDecoder: Decoder[WorkspaceFolder] =
      deriveDecoder
  }

  /** A param of the request [[org.enso.gateway.protocol.Requests.Initialize]].
    *
    * The capabilities provided by the client (editor or tool).
    * Define capabilities for dynamic registration, workspace and text document
    * features the client supports.
    *
    * @see [[org.enso.gateway.protocol.request.Params.InitializeParams]].
    */
  case class ClientCapabilities(
    workspace: Option[clientcapabilities.Workspace]       = None,
    textDocument: Option[clientcapabilities.TextDocument] = None,
    experimental: Option[clientcapabilities.Experimental] = None
  ) extends Param

  object ClientCapabilities {
    implicit val clientCapabilitiesDecoder: Decoder[ClientCapabilities] =
      deriveDecoder
  }

  /**
    *
    */
  case class TextDocumentItem(
    uri: DocumentUri,
    languageId: String,
    version: Int,
    text: String
  ) extends Param

  object TextDocumentItem {
    implicit val textDocumentItemDecoder: Decoder[TextDocumentItem] =
      deriveDecoder
  }

  case class WorkspaceEdit(
    changes: Option[Map[DocumentUri, Seq[TextEdit]]],
    documentChanges: Option[DocumentChanges]
  ) extends Param

  object WorkspaceEdit {
    implicit val workspaceEditDecoder: Decoder[WorkspaceEdit] =
      deriveDecoder
  }

  case class TextDocumentIdentifier(
    uri: DocumentUri
  ) extends Param

  object TextDocumentIdentifier {
    implicit val textDocumentIdentifierDecoder
      : Decoder[TextDocumentIdentifier] =
      deriveDecoder
  }

  case class VersionedTextDocumentIdentifier(
    uri: DocumentUri,
    version: Option[Int] = None
  ) extends Param

  object VersionedTextDocumentIdentifier {
    implicit val versionedTextDocumentIdentifierDecoder
      : Decoder[VersionedTextDocumentIdentifier] =
      deriveDecoder
  }

  sealed abstract class TextDocumentSaveReason(value: Int) extends Param

  object TextDocumentSaveReason {

    case object Manual extends TextDocumentSaveReason(1)

    case object AfterDelay extends TextDocumentSaveReason(2)

    case object FocusOut extends TextDocumentSaveReason(3)

    implicit val textDocumentSaveReasonDecoder
      : Decoder[TextDocumentSaveReason] =
      Decoder.decodeInt.emap {
        case 1 => Right(Manual)
        case 2 => Right(AfterDelay)
        case 3 => Right(FocusOut)
        case _ => Left("Invalid TextDocumentSaveReason")
      }
  }

  sealed trait TextDocumentContentChangeEvent extends Param

  object TextDocumentContentChangeEvent {

    case class RangeChange(
      range: TextRange,
      rangeLength: Option[Int],
      text: String
    ) extends TextDocumentContentChangeEvent

    object RangeChange {
      implicit val textDocumentContentChangeEventRangeChangeDecoder
        : Decoder[RangeChange] =
        deriveDecoder
    }

    case class WholeDocumentChange(text: String)
        extends TextDocumentContentChangeEvent

    object WholeDocumentChange {
      implicit val textDocumentContentChangeEventWholeDocumentChangeDecoder
        : Decoder[WholeDocumentChange] =
        deriveDecoder
    }

    implicit val textDocumentContentChangeEventDecoder
      : Decoder[TextDocumentContentChangeEvent] =
      List[Decoder[TextDocumentContentChangeEvent]](
        Decoder[RangeChange].widen,
        Decoder[WholeDocumentChange].widen
      ).reduceLeft(_ or _)
  }

}
