package org.enso.gateway.protocol.request

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._

sealed trait AnyDocumentChange

object AnyDocumentChange {

  case class TextDocumentEditChange(value: TextDocumentEdit)
      extends AnyDocumentChange

  object TextDocumentEditChange {
    implicit val anyDocumentChangeTextDocumentEditChangeDecoder
      : Decoder[TextDocumentEditChange] = deriveUnwrappedDecoder

    implicit val anyDocumentChangeTextDocumentEditChangeEncoder
      : Encoder[TextDocumentEditChange] = deriveUnwrappedEncoder
  }

  case class CreateFileChange(value: ChangeFile.Create)
      extends AnyDocumentChange
  object CreateFileChange {
    implicit val anyDocumentChangeCreateFileChangeDecoder
      : Decoder[CreateFileChange] = deriveUnwrappedDecoder

    implicit val anyDocumentChangeCreateFileChangeEncoder
      : Encoder[CreateFileChange] = deriveUnwrappedEncoder
  }

  case class RenameFileChange(value: ChangeFile.Rename)
      extends AnyDocumentChange
  object RenameFileChange {
    implicit val anyDocumentChangeRenameFileChangeDecoder
      : Decoder[RenameFileChange] = deriveUnwrappedDecoder

    implicit val anyDocumentChangeRenameFileChangeEncoder
      : Encoder[RenameFileChange] = deriveUnwrappedEncoder
  }

  case class DeleteFileChange(value: ChangeFile.Delete)
      extends AnyDocumentChange
  object DeleteFileChange {
    implicit val anyDocumentChangeDeleteFileChangeDecoder
      : Decoder[DeleteFileChange] = deriveUnwrappedDecoder

    implicit val anyDocumentChangeDeleteFileChangeEncoder
      : Encoder[DeleteFileChange] = deriveUnwrappedEncoder
  }

  implicit val anyDocumentChangeDecoder: Decoder[AnyDocumentChange] =
    List[Decoder[AnyDocumentChange]](
      Decoder[TextDocumentEditChange].widen,
      Decoder[CreateFileChange].widen,
      Decoder[RenameFileChange].widen,
      Decoder[DeleteFileChange].widen
    ).reduceLeft(_ or _)

  implicit val anyDocumentChangeEncoder: Encoder[AnyDocumentChange] =
    Encoder.instance {
      case change: TextDocumentEditChange => change.asJson
      case change: CreateFileChange       => change.asJson
      case change: RenameFileChange       => change.asJson
      case change: DeleteFileChange       => change.asJson
    }
}
