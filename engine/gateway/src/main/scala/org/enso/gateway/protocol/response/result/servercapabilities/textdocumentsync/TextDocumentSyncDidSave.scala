package org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync

import io.circe.Encoder
import io.circe.syntax._
import io.circe.generic.extras.semiauto.deriveUnwrappedEncoder
import io.circe.generic.semiauto.deriveEncoder

sealed trait TextDocumentSyncDidSave

object TextDocumentSyncDidSave {

  case class Bool(value: Boolean) extends TextDocumentSyncDidSave

  object Bool {
    implicit val textDocumentSyncDidSaveBoolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
  }

  case class SaveOptions(includeText: Option[Boolean] = None)
      extends TextDocumentSyncDidSave

  object SaveOptions {
    implicit val textDocumentSyncDidSaveSaveOptionsEncoder
      : Encoder[SaveOptions] =
      deriveEncoder
  }

  implicit val textDocumentSyncDidSaveEncoder
    : Encoder[TextDocumentSyncDidSave] =
    Encoder.instance {
      case boolean: Bool        => boolean.asJson
      case options: SaveOptions => options.asJson
    }
}
