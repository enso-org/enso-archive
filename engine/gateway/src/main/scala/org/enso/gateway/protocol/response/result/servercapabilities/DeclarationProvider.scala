package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import org.enso.gateway.protocol.response.result.ServerCapabilities.DocumentSelector
import cats.syntax.functor._

/** Server capability to provide "go to declaration" support. */
sealed trait DeclarationProvider
object DeclarationProvider {

  case class Bool(value: Boolean) extends DeclarationProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DeclarationOptions(workDoneProgress: Option[Boolean] = None)
      extends DeclarationProvider
  object DeclarationOptions {
    implicit val declarationOptionsEncoder: Encoder[DeclarationOptions] =
      deriveEncoder
    implicit val declarationOptionsDecoder: Decoder[DeclarationOptions] =
      deriveDecoder
  }

  case class DeclarationRegistrationOptions(
    workDoneProgress: Option[Boolean]          = None,
    documentSelector: Option[DocumentSelector] = None,
    id: Option[String]                         = None
  ) extends DeclarationProvider
  object DeclarationRegistrationOptions {
    implicit val declarationRegistrationOptionsEncoder
      : Encoder[DeclarationRegistrationOptions] =
      deriveEncoder
    implicit val declarationRegistrationOptionsDecoder
      : Decoder[DeclarationRegistrationOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDeclarationProviderEncoder
    : Encoder[DeclarationProvider] =
    Encoder.instance {
      case boolean: Bool                           => boolean.asJson
      case options: DeclarationOptions             => options.asJson
      case options: DeclarationRegistrationOptions => options.asJson
    }

  implicit val serverCapabilitiesDeclarationProviderDecoder
    : Decoder[DeclarationProvider] =
    List[Decoder[DeclarationProvider]](
      Decoder[Bool].widen,
      Decoder[DeclarationOptions].widen,
      Decoder[DeclarationRegistrationOptions].widen
    ).reduceLeft(_ or _)
}
