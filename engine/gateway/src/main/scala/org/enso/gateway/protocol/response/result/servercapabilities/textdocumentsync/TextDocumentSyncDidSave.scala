package org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

sealed trait TextDocumentSyncDidSave

object TextDocumentSyncDidSave {

  case class Bool(value: Boolean) extends TextDocumentSyncDidSave

  object Bool {
    implicit val textDocumentSyncDidSaveBoolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val textDocumentSyncDidSaveBoolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class SaveOptions(includeText: Option[Boolean] = None)
      extends TextDocumentSyncDidSave
  object SaveOptions {
    implicit val textDocumentSyncDidSaveSaveOptionsEncoder
      : Encoder[SaveOptions] = deriveEncoder
    implicit val textDocumentSyncDidSaveSaveOptionsDecoder
      : Decoder[SaveOptions] = deriveDecoder
  }

  implicit val textDocumentSyncDidSaveEncoder
    : Encoder[TextDocumentSyncDidSave] =
    Encoder.instance {
      case boolean: Bool        => boolean.asJson
      case options: SaveOptions => options.asJson
    }

  implicit val textDocumentSyncDidSaveDecoder
    : Decoder[TextDocumentSyncDidSave] =
    List[Decoder[TextDocumentSyncDidSave]](
      Decoder[Bool].widen,
      Decoder[SaveOptions].widen
    ).reduceLeft(_ or _)
}
