package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide "go to definition" support. */
sealed trait DefinitionProvider
object DefinitionProvider {

  case class Bool(value: Boolean) extends DefinitionProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  case class DefinitionOptions(workDoneProgress: Option[Boolean] = None)
      extends DefinitionProvider
  object DefinitionOptions {
    implicit val definitionOptionsEncoder: Encoder[DefinitionOptions] =
      deriveEncoder
    implicit val definitionOptionsDecoder: Decoder[DefinitionOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesDefinitionProviderEncoder
    : Encoder[DefinitionProvider] =
    Encoder.instance {
      case boolean: Bool              => boolean.asJson
      case options: DefinitionOptions => options.asJson
    }

  implicit val serverCapabilitiesDefinitionProviderDecoder
    : Decoder[DefinitionProvider] =
    List[Decoder[DefinitionProvider]](
      Decoder[Bool].widen,
      Decoder[DefinitionOptions].widen
    ).reduceLeft(_ or _)
}
