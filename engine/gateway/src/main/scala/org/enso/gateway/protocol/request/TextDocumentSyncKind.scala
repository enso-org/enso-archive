package org.enso.gateway.protocol.request

import io.circe.Decoder

sealed abstract class TextDocumentSyncKind(value: Int)

object TextDocumentSyncKind {

  object NoneKind extends TextDocumentSyncKind(0)

  object Full extends TextDocumentSyncKind(1)

  object Incremental extends TextDocumentSyncKind(2)

  implicit val textDocumentSyncKindDecoder: Decoder[TextDocumentSyncKind] =
    Decoder.decodeInt.emap {
      case 0 => Right(NoneKind)
      case 1 => Right(Full)
      case 2 => Right(Incremental)
      case _ => Left("Invalid TextDocumentSyncKind")
    }
}
