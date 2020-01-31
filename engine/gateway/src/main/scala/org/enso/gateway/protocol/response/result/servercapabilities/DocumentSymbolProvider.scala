package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide document symbol support. */
sealed trait DocumentSymbolProvider
object DocumentSymbolProvider {

  case class Bool(value: Boolean) extends DocumentSymbolProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DocumentSymbolOptions(workDoneProgress: Option[Boolean] = None)
      extends DocumentSymbolProvider
  object DocumentSymbolOptions {
    implicit val documentSymbolOptionsEncoder: Encoder[DocumentSymbolOptions] =
      deriveEncoder
    implicit val documentSymbolOptionsDecoder: Decoder[DocumentSymbolOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDocumentSymbolProviderEncoder
    : Encoder[DocumentSymbolProvider] =
    Encoder.instance {
      case boolean: Bool                  => boolean.asJson
      case options: DocumentSymbolOptions => options.asJson
    }

  implicit val serverCapabilitiesDocumentSymbolProviderDecoder
    : Decoder[DocumentSymbolProvider] =
    List[Decoder[DocumentSymbolProvider]](
      Decoder[Bool].widen,
      Decoder[DocumentSymbolOptions].widen
    ).reduceLeft(_ or _)
}
