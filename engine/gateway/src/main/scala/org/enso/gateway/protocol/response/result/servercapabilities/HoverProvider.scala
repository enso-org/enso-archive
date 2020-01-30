package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import cats.syntax.functor._

/** Server capability to provide hover support. */
sealed trait HoverProvider

object HoverProvider {

  case class Bool(value: Boolean) extends HoverProvider

  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class HoverOptions(workDoneProgress: Option[Boolean] = None)
      extends HoverProvider

  object HoverOptions {
    implicit val hoverOptionsEncoder: Encoder[HoverOptions] = deriveEncoder
    implicit val hoverOptionsDecoder: Decoder[HoverOptions] = deriveDecoder
  }

  implicit val serverCapabilitiesHoverProviderEncoder: Encoder[HoverProvider] =
    Encoder.instance {
      case boolean: Bool         => boolean.asJson
      case options: HoverOptions => options.asJson
    }

  implicit val serverCapabilitiesHoverProviderDecoder: Decoder[HoverProvider] =
    List[Decoder[HoverProvider]](
      Decoder[Bool].widen,
      Decoder[HoverOptions].widen
    ).reduceLeft(_ or _)
}
