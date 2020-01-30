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

/** Server capability to provide "go to type definition" support. */
sealed trait TypeDefinitionProvider

object TypeDefinitionProvider {

  case class Bool(value: Boolean) extends TypeDefinitionProvider

  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class TypeDefinitionOptions(workDoneProgress: Option[Boolean] = None)
      extends TypeDefinitionProvider
  object TypeDefinitionOptions {
    implicit val typeDefinitionOptionsEncoder: Encoder[TypeDefinitionOptions] =
      deriveEncoder
    implicit val typeDefinitionOptionsDecoder: Decoder[TypeDefinitionOptions] =
      deriveDecoder
  }

  case class TypeDefinitionRegistrationOptions(
    documentSelector: Option[DocumentSelector] = None,
    workDoneProgress: Option[Boolean]          = None,
    id: Option[String]                         = None
  ) extends TypeDefinitionProvider

  object TypeDefinitionRegistrationOptions {
    implicit val typeDefinitionRegistrationOptionsEncoder
      : Encoder[TypeDefinitionRegistrationOptions] =
      deriveEncoder
    implicit val typeDefinitionRegistrationOptionsDecoder
      : Decoder[TypeDefinitionRegistrationOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesTypeDefinitionProviderEncoder
    : Encoder[TypeDefinitionProvider] =
    Encoder.instance {
      case boolean: Bool                              => boolean.asJson
      case options: TypeDefinitionOptions             => options.asJson
      case options: TypeDefinitionRegistrationOptions => options.asJson
    }

  implicit val serverCapabilitiesTypeDefinitionProviderDecoder
    : Decoder[TypeDefinitionProvider] =
    List[Decoder[TypeDefinitionProvider]](
      Decoder[Bool].widen,
      Decoder[TypeDefinitionOptions].widen,
      Decoder[TypeDefinitionRegistrationOptions].widen
    ).reduceLeft(_ or _)
}
