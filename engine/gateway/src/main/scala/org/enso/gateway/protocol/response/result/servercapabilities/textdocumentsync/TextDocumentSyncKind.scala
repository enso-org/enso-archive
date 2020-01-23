package org.enso.gateway.protocol.response.result.servercapabilities.textdocumentsync

import io.circe.Encoder

sealed abstract class TextDocumentSyncKind(val value: Int)

object TextDocumentSyncKind {
  private val none        = 0
  private val full        = 1
  private val incremental = 2

  object NoneKind extends TextDocumentSyncKind(none)

  object Full extends TextDocumentSyncKind(full)

  object Incremental extends TextDocumentSyncKind(incremental)

  implicit val textDocumentSyncKindEncoder: Encoder[TextDocumentSyncKind] =
    Encoder.encodeInt.contramap(_.value)
}
