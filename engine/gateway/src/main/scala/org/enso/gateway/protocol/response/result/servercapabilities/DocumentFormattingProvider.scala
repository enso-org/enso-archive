package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide document formatting. */
sealed trait DocumentFormattingProvider
object DocumentFormattingProvider {

  case class Bool(value: Boolean) extends DocumentFormattingProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DocumentFormattingOptions(workDoneProgress: Option[Boolean] = None)
      extends DocumentFormattingProvider
  object DocumentFormattingOptions {
    implicit val documentFormattingOptionsEncoder
      : Encoder[DocumentFormattingOptions] =
      deriveEncoder
    implicit val documentFormattingOptionsDecoder
      : Decoder[DocumentFormattingOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDocumentFormattingProviderEncoder
    : Encoder[DocumentFormattingProvider] =
    Encoder.instance {
      case boolean: Bool                      => boolean.asJson
      case options: DocumentFormattingOptions => options.asJson
    }

  implicit val serverCapabilitiesDocumentFormattingProviderDecoder
    : Decoder[DocumentFormattingProvider] =
    List[Decoder[DocumentFormattingProvider]](
      Decoder[Bool].widen,
      Decoder[DocumentFormattingOptions].widen
    ).reduceLeft(_ or _)
}
