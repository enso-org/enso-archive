package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import cats.syntax.functor._
import io.circe.syntax._

/**
  * Id of [[RequestOrNotification]] or [[Response]]
  */
sealed trait Id

object Id {
  implicit val idEncoder: Encoder[Id] = Encoder.instance {
    case number: Number => number.asJson
    case string: String => string.asJson
  }

  implicit val idDecoder: Decoder[Id] = List[Decoder[Id]](
    Decoder[Number].widen,
    Decoder[String].widen
  ).reduceLeft(_ or _)

  /**
    * A number id
    */
  case class Number(value: Int) extends Id

  object Number {
    implicit val idNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val idNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /**
    * A string id
    */
  case class String(value: Predef.String) extends Id

  object String {
    implicit val idStringEncoder: Encoder[String] = deriveUnwrappedEncoder
    implicit val idStringDecoder: Decoder[String] = deriveUnwrappedDecoder
  }

}
