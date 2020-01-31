package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide document highlight support. */
sealed trait DocumentHighlightProvider
object DocumentHighlightProvider {

  case class Bool(value: Boolean) extends DocumentHighlightProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DocumentHighlightOptions(workDoneProgress: Option[Boolean] = None)
      extends DocumentHighlightProvider
  object DocumentHighlightOptions {
    implicit val documentHighlightOptionsEncoder
      : Encoder[DocumentHighlightOptions] =
      deriveEncoder
    implicit val documentHighlightOptionsDecoder
      : Decoder[DocumentHighlightOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDocumentHighlightProviderEncoder
    : Encoder[DocumentHighlightProvider] =
    Encoder.instance {
      case boolean: Bool                     => boolean.asJson
      case options: DocumentHighlightOptions => options.asJson
    }

  implicit val serverCapabilitiesDocumentHighlightProviderDecoder
    : Decoder[DocumentHighlightProvider] =
    List[Decoder[DocumentHighlightProvider]](
      Decoder[Bool].widen,
      Decoder[DocumentHighlightOptions].widen
    ).reduceLeft(_ or _)
}
