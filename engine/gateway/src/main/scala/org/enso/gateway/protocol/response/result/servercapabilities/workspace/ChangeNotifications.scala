package org.enso.gateway.protocol.response.result.servercapabilities.workspace

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._

/** Part of [[WorkspaceFoldersServerCapabilities]]. */
sealed trait ChangeNotifications

object ChangeNotifications {

  /** String [[ChangeNotifications]]. */
  case class Text(value: String) extends ChangeNotifications

  object Text {
    implicit val textEncoder: Encoder[Text] =
      deriveUnwrappedEncoder
    implicit val textDecoder: Decoder[Text] =
      deriveUnwrappedDecoder
  }

  /** Boolean [[ChangeNotifications]]. */
  case class Bool(value: Boolean) extends ChangeNotifications
  object Bool {
    implicit val boolEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val boolDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  implicit val textEncoder: Encoder[ChangeNotifications] =
    Encoder.instance {
      case text: Text    => text.asJson
      case boolean: Bool => boolean.asJson
    }

  implicit val textDecoder: Decoder[ChangeNotifications] =
    List[Decoder[ChangeNotifications]](
      Decoder[Text].widen,
      Decoder[Bool].widen
    ).reduceLeft(_ or _)
}
