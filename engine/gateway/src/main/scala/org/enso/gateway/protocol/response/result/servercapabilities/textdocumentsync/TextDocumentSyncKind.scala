package org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync

import io.circe.Encoder

sealed abstract class TextDocumentSyncKind(val value: Int)

object TextDocumentSyncKind {

  object NoneKind extends TextDocumentSyncKind(0)

  object Full extends TextDocumentSyncKind(1)

  object Incremental extends TextDocumentSyncKind(2)

  implicit val textDocumentSyncKindEncoder: Encoder[TextDocumentSyncKind] =
    Encoder.encodeInt.contramap(_.value)
}
