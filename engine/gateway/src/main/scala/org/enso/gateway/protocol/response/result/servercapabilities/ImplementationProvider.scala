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

/** Server capability to provide "go to implementation" support. */
sealed trait ImplementationProvider

object ImplementationProvider {

  case class Bool(value: Boolean) extends ImplementationProvider

  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class ImplementationOptions(workDoneProgress: Option[Boolean] = None)
      extends ImplementationProvider
  object ImplementationOptions {
    implicit val implementationOptionsEncoder: Encoder[ImplementationOptions] =
      deriveEncoder
    implicit val implementationOptionsDecoder: Decoder[ImplementationOptions] =
      deriveDecoder
  }

  case class ImplementationRegistrationOptions(
    documentSelector: Option[DocumentSelector] = None,
    workDoneProgress: Option[Boolean]          = None,
    id: Option[String]                         = None
  ) extends ImplementationProvider
  object ImplementationRegistrationOptions {
    implicit val implementationRegistrationOptionsEncoder
      : Encoder[ImplementationRegistrationOptions] =
      deriveEncoder
    implicit val implementationRegistrationOptionsDecoder
      : Decoder[ImplementationRegistrationOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesImplementationProviderEncoder
    : Encoder[ImplementationProvider] =
    Encoder.instance {
      case boolean: Bool                              => boolean.asJson
      case options: ImplementationOptions             => options.asJson
      case options: ImplementationRegistrationOptions => options.asJson
    }

  implicit val serverCapabilitiesImplementationProviderDecoder
    : Decoder[ImplementationProvider] =
    List[Decoder[ImplementationProvider]](
      Decoder[Bool].widen,
      Decoder[ImplementationOptions].widen,
      Decoder[ImplementationRegistrationOptions].widen
    ).reduceLeft(_ or _)
}
