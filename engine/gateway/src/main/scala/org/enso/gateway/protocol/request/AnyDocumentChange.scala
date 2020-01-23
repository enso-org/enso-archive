package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import cats.syntax.functor._

sealed trait AnyDocumentChange

object AnyDocumentChange {

  case class TextDocumentEditChange(value: TextDocumentEdit)
      extends AnyDocumentChange

  object TextDocumentEditChange {
    implicit val anyDocumentChangeTextDocumentEditChangeDecoder
      : Decoder[TextDocumentEditChange] =
      deriveUnwrappedDecoder
  }

  case class CreateFileChange(value: ChangeFile.Create)
      extends AnyDocumentChange

  object CreateFileChange {
    implicit val anyDocumentChangeCreateFileChangeDecoder
      : Decoder[CreateFileChange] =
      deriveUnwrappedDecoder
  }

  case class RenameFileChange(value: ChangeFile.Rename)
      extends AnyDocumentChange

  object RenameFileChange {
    implicit val anyDocumentChangeRenameFileChangeDecoder
      : Decoder[RenameFileChange] =
      deriveUnwrappedDecoder
  }

  case class DeleteFileChange(value: ChangeFile.Delete)
      extends AnyDocumentChange

  object DeleteFileChange {
    implicit val anyDocumentChangeDeleteFileChangeDecoder
      : Decoder[DeleteFileChange] =
      deriveUnwrappedDecoder
  }

  implicit val anyDocumentChangeDecoder: Decoder[AnyDocumentChange] =
    List[Decoder[AnyDocumentChange]](
      Decoder[TextDocumentEditChange].widen,
      Decoder[CreateFileChange].widen,
      Decoder[RenameFileChange].widen,
      Decoder[DeleteFileChange].widen
    ).reduceLeft(_ or _)
}
