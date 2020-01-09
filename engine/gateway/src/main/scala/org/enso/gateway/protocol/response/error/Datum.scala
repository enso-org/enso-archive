package org.enso.gateway.protocol.response.error

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import cats.syntax.functor._
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * An element of [[Data.Array]]
  */
sealed trait Datum

object Datum {
  implicit val datumDecoder: Decoder[Datum] = List[Decoder[Datum]](
    Decoder[Number].widen,
    Decoder[Boolean].widen,
    Decoder[Array].widen,
    Decoder[String].widen
  ).reduceLeft(_ or _)

  case class String(value: Predef.String) extends Datum

  object String {
    implicit val datumStringEncoder: Encoder[String] = deriveUnwrappedEncoder
    implicit val datumStringDecoder: Decoder[String] = deriveUnwrappedDecoder
  }

  case class Number(value: Int) extends Datum

  object Number {
    implicit val datumNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val datumNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  case class Boolean(value: scala.Boolean) extends Datum

  object Boolean {
    implicit val datumBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
    implicit val datumBooleanDecoder: Decoder[Boolean] = deriveUnwrappedDecoder
  }

  case class Array(value: Seq[Option[Datum]]) extends Datum

  object Array {
    implicit val datumArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
    implicit val datumArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
  }

}
