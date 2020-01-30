package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync.{
  TextDocumentSyncDidSave,
  TextDocumentSyncKind
}
import cats.syntax.functor._

/** Defines how text documents are synced.
  *
  * Is either a detailed structure defining each notification or for backwards
  * compatibility the [[TextDocumentSyncKind]] number. If omitted it defaults to
  * [[TextDocumentSyncKind.NoneKind]].
  */
sealed trait TextDocumentSync
object TextDocumentSync {

  case class Number(value: Int) extends TextDocumentSync
  object Number {
    implicit val textDocumentSyncNumberEncoder: Encoder[Number] =
      deriveUnwrappedEncoder
    implicit val textDocumentSyncNumberDecoder: Decoder[Number] =
      deriveUnwrappedDecoder
  }

  case class TextDocumentSyncOptions(
    openClose: Option[Boolean]               = None,
    change: Option[TextDocumentSyncKind]     = None,
    willSaveWaitUntil: Option[Boolean]       = None,
    didSave: Option[TextDocumentSyncDidSave] = None
  ) extends TextDocumentSync
  object TextDocumentSyncOptions {
    implicit val textDocumentSyncTextDocumentSyncOptionsEncoder
      : Encoder[TextDocumentSyncOptions] = deriveEncoder
    implicit val textDocumentSyncTextDocumentSyncOptionsDecoder
      : Decoder[TextDocumentSyncOptions] = deriveDecoder
  }

  implicit val serverCapabilitiesTextDocumentSyncEncoder
    : Encoder[TextDocumentSync] = Encoder.instance {
    case number: Number                      => number.asJson
    case capability: TextDocumentSyncOptions => capability.asJson
  }

  implicit val serverCapabilitiesTextDocumentSyncDecoder
    : Decoder[TextDocumentSync] = List[Decoder[TextDocumentSync]](
    Decoder[Number].widen,
    Decoder[TextDocumentSyncOptions].widen
  ).reduceLeft(_ or _)
}
