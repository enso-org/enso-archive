package org.enso.gateway.protocol.response.error

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import cats.syntax.functor._

/** An element of [[Data.Array]]. */
sealed trait Datum
object Datum {
  implicit val datumEncoder: Encoder[Datum] = Encoder.instance {
    case text: Text     => text.asJson
    case number: Number => number.asJson
    case boolean: Bool  => boolean.asJson
    case array: Array   => array.asJson
  }

  implicit val datumDecoder: Decoder[Datum] = List[Decoder[Datum]](
    Decoder[Text].widen,
    Decoder[Number].widen,
    Decoder[Bool].widen,
    Decoder[Array].widen
  ).reduceLeft(_ or _)

  /** A string element. */
  case class Text(value: String) extends Datum

  object Text {
    implicit val datumStringEncoder: Encoder[Text] = deriveUnwrappedEncoder
    implicit val datumStringDecoder: Decoder[Text] = deriveUnwrappedDecoder
  }

  /** A number element. */
  case class Number(value: Int) extends Datum
  object Number {
    implicit val datumNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val datumNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /** A boolean element. */
  case class Bool(value: Boolean) extends Datum
  object Bool {
    implicit val datumBooleanEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val datumBooleanDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  /** An array element. */
  case class Array(value: Seq[Option[Datum]]) extends Datum
  object Array {
    implicit val datumArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
    implicit val datumArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
  }
}
