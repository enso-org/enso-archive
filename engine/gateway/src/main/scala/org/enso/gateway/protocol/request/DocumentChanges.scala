package org.enso.gateway.protocol.request

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._

sealed trait DocumentChanges
object DocumentChanges {

  case class TextDocumentEdits(value: Seq[TextDocumentEdit])
      extends DocumentChanges
  object TextDocumentEdits {
    implicit val documentChangesTextDocumentEditsDecoder
      : Decoder[TextDocumentEdits] = deriveUnwrappedDecoder
    implicit val documentChangesTextDocumentEditsEncoder
      : Encoder[TextDocumentEdits] = deriveUnwrappedEncoder
  }

  case class AnyDocumentChanges(value: Seq[AnyDocumentChange])
      extends DocumentChanges
  object AnyDocumentChanges {
    implicit val documentChangesAnyDocumentChangesDecoder
      : Decoder[AnyDocumentChanges] = deriveUnwrappedDecoder
    implicit val documentChangesAnyDocumentChangesEncoder
      : Encoder[AnyDocumentChanges] = deriveUnwrappedEncoder
  }

  implicit val documentChangesDecoder: Decoder[DocumentChanges] =
    List[Decoder[DocumentChanges]](
      Decoder[TextDocumentEdits].widen,
      Decoder[AnyDocumentChanges].widen
    ).reduceLeft(_ or _)

  implicit val documentChangesEncoder: Encoder[DocumentChanges] =
    Encoder.instance {
      case changes: TextDocumentEdits  => changes.asJson
      case changes: AnyDocumentChanges => changes.asJson
    }
}
