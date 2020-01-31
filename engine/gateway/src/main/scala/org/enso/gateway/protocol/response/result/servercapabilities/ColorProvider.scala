package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.response.result.ServerCapabilities.DocumentSelector
import cats.syntax.functor._

/** Server capability to provide color provider support. */
sealed trait ColorProvider
object ColorProvider {

  case class Bool(value: Boolean) extends ColorProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DocumentColorOptions(workDoneProgress: Option[Boolean] = None)
      extends ColorProvider
  object DocumentColorOptions {
    implicit val documentColorOptionsEncoder: Encoder[DocumentColorOptions] =
      deriveEncoder
    implicit val documentColorOptionsDecoder: Decoder[DocumentColorOptions] =
      deriveDecoder
  }

  case class DocumentColorRegistrationOptions(
    workDoneProgress: Option[Boolean]          = None,
    documentSelector: Option[DocumentSelector] = None,
    id: Option[String]                         = None
  ) extends ColorProvider
  object DocumentColorRegistrationOptions {
    implicit val documentColorRegistrationOptionsEncoder
      : Encoder[DocumentColorRegistrationOptions] =
      deriveEncoder
    implicit val documentColorRegistrationOptionsDecoder
      : Decoder[DocumentColorRegistrationOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesColorProviderEncoder: Encoder[ColorProvider] =
    Encoder.instance {
      case boolean: Bool                             => boolean.asJson
      case options: DocumentColorOptions             => options.asJson
      case options: DocumentColorRegistrationOptions => options.asJson
    }

  implicit val serverCapabilitiesColorProviderDecoder: Decoder[ColorProvider] =
    List[Decoder[ColorProvider]](
      Decoder[Bool].widen,
      Decoder[DocumentColorOptions].widen,
      Decoder[DocumentColorRegistrationOptions].widen
    ).reduceLeft(_ or _)
}
