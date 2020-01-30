package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide document range formatting. */
sealed trait DocumentRangeFormattingProvider

object DocumentRangeFormattingProvider {

  case class Bool(value: Boolean) extends DocumentRangeFormattingProvider

  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DocumentRangeFormattingOptions(
    workDoneProgress: Option[Boolean] = None
  ) extends DocumentRangeFormattingProvider
  object DocumentRangeFormattingOptions {
    implicit val documentRangeFormattingOptiondEncoder
      : Encoder[DocumentRangeFormattingOptions] =
      deriveEncoder
    implicit val documentRangeFormattingOptiondDecoder
      : Decoder[DocumentRangeFormattingOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDocumentRangeFormattingProviderEncoder
    : Encoder[DocumentRangeFormattingProvider] =
    Encoder.instance {
      case boolean: Bool                           => boolean.asJson
      case options: DocumentRangeFormattingOptions => options.asJson
    }

  implicit val serverCapabilitiesDocumentRangeFormattingProviderDecoder
    : Decoder[DocumentRangeFormattingProvider] =
    List[Decoder[DocumentRangeFormattingProvider]](
      Decoder[Bool].widen,
      Decoder[DocumentRangeFormattingOptions].widen
    ).reduceLeft(_ or _)
}
