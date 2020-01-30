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

/** Server capability to provide folding provider support. */
sealed trait FoldingRangeProvider

object FoldingRangeProvider {

  case class Bool(value: Boolean) extends FoldingRangeProvider

  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class FoldingRangeOptions(workDoneProgress: Option[Boolean] = None)
      extends FoldingRangeProvider
  object FoldingRangeOptions {
    implicit val foldingRangeOptionsEncoder: Encoder[FoldingRangeOptions] =
      deriveEncoder
    implicit val foldingRangeOptionsDecoder: Decoder[FoldingRangeOptions] =
      deriveDecoder
  }

  case class FoldingRangeRegistrationOptions(
    documentSelector: Option[DocumentSelector] = None,
    workDoneProgress: Option[Boolean]          = None,
    id: Option[String]                         = None
  ) extends FoldingRangeProvider
  object FoldingRangeRegistrationOptions {
    implicit val foldingRangeRegistrationOptionsEncoder
      : Encoder[FoldingRangeRegistrationOptions] =
      deriveEncoder
    implicit val foldingRangeRegistrationOptionsDecoder
      : Decoder[FoldingRangeRegistrationOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesFoldingRangeProviderEncoder
    : Encoder[FoldingRangeProvider] =
    Encoder.instance {
      case boolean: Bool                            => boolean.asJson
      case options: FoldingRangeOptions             => options.asJson
      case options: FoldingRangeRegistrationOptions => options.asJson
    }

  implicit val serverCapabilitiesFoldingRangeProviderDecoder
    : Decoder[FoldingRangeProvider] =
    List[Decoder[FoldingRangeProvider]](
      Decoder[Bool].widen,
      Decoder[FoldingRangeOptions].widen,
      Decoder[FoldingRangeRegistrationOptions].widen
    ).reduceLeft(_ or _)
}
