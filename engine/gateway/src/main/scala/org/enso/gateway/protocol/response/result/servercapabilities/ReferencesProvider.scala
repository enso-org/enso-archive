package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide find references support. */
sealed trait ReferencesProvider
object ReferencesProvider {

  case class Bool(value: Boolean) extends ReferencesProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class ReferenceOptions(workDoneProgress: Option[Boolean] = None)
      extends ReferencesProvider
  object ReferenceOptions {
    implicit val referenceOptionsEncoder: Encoder[ReferenceOptions] =
      deriveEncoder
    implicit val referenceOptionsDecoder: Decoder[ReferenceOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesReferencesProviderEncoder
    : Encoder[ReferencesProvider] =
    Encoder.instance {
      case boolean: Bool             => boolean.asJson
      case options: ReferenceOptions => options.asJson
    }

  implicit val serverCapabilitiesReferencesProviderDecoder
    : Decoder[ReferencesProvider] =
    List[Decoder[ReferencesProvider]](
      Decoder[Bool].widen,
      Decoder[ReferenceOptions].widen
    ).reduceLeft(_ or _)
}
