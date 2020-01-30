package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

/** Server capability to provide rename support.
  *
  * [[RenameProvider.RenameOptions]] may only be specified if
  * the client states that it supports `prepareSupport` in its initial
  * `initialize` request.
  */
sealed trait RenameProvider

object RenameProvider {

  case class Bool(value: Boolean) extends RenameProvider
  object Bool {
    implicit val boolEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  case class RenameOptions(
    workDoneProgress: Option[Boolean] = None,
    prepareProvider: Option[Boolean]  = None
  ) extends RenameProvider
  object RenameOptions {
    implicit val renameOptionsEncoder: Encoder[RenameOptions] =
      deriveEncoder
    implicit val renameOptionsDecoder: Decoder[RenameOptions] =
      deriveDecoder
  }

  implicit val serverCapabilitiesRenameProviderEncoder
    : Encoder[RenameProvider] =
    Encoder.instance {
      case boolean: Bool          => boolean.asJson
      case options: RenameOptions => options.asJson
    }

  implicit val serverCapabilitiesRenameProviderDecoder
    : Decoder[RenameProvider] =
    List[Decoder[RenameProvider]](
      Decoder[Bool].widen,
      Decoder[RenameOptions].widen
    ).reduceLeft(_ or _)
}
