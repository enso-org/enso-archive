package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import cats.syntax.functor._

sealed trait DocumentChanges

object DocumentChanges {

  case class TextDocumentEdits(value: Seq[TextDocumentEdit])
      extends DocumentChanges

  object TextDocumentEdits {
    implicit val documentChangesTextDocumentEditsDecoder
      : Decoder[TextDocumentEdits] =
      deriveUnwrappedDecoder
  }

  case class AnyDocumentChanges(value: Seq[AnyDocumentChange])
      extends DocumentChanges

  object AnyDocumentChanges {
    implicit val documentChangesAnyDocumentChangesDecoder
      : Decoder[AnyDocumentChanges] =
      deriveUnwrappedDecoder
  }

  implicit val documentChangesDecoder: Decoder[DocumentChanges] =
    List[Decoder[DocumentChanges]](
      Decoder[TextDocumentEdits].widen,
      Decoder[AnyDocumentChanges].widen
    ).reduceLeft(_ or _)
}
