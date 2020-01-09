package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._

/**
  * Id of [[RequestOrNotification]] or [[Response]]
  */
sealed trait Id

object Id {
  implicit val idDecoder: Decoder[Id] = List[Decoder[Id]](
    Decoder[Number].widen,
    Decoder[String].widen
  ).reduceLeft(_ or _)

  case class Number(value: Int) extends Id

  object Number {
    implicit val idNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val idNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  case class String(value: Predef.String) extends Id

  object String {
    implicit val idStringEncoder: Encoder[String] = deriveUnwrappedEncoder
    implicit val idStringDecoder: Decoder[String] = deriveUnwrappedDecoder
  }

}
