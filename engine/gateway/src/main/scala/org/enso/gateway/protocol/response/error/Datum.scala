package org.enso.gateway.protocol.response.error

import io.circe.Encoder
import io.circe.generic.extras.semiauto._
import io.circe.syntax._

/**
  * An element of [[Data.Array]]
  */
sealed trait Datum

object Datum {
  implicit val datumEncoder: Encoder[Datum] = Encoder.instance {
    case string: String   => string.asJson
    case number: Number   => number.asJson
    case boolean: Boolean => boolean.asJson
    case array: Array     => array.asJson
  }

  /**
    * A string element
    */
  case class String(value: Predef.String) extends Datum

  object String {
    implicit val datumStringEncoder: Encoder[String] = deriveUnwrappedEncoder
  }

  /**
    * A number element
    */
  case class Number(value: Int) extends Datum

  object Number {
    implicit val datumNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
  }

  /**
    * A boolean element
    */
  case class Boolean(value: scala.Boolean) extends Datum

  object Boolean {
    implicit val datumBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
  }

  /**
    * An array element
    */
  case class Array(value: Seq[Option[Datum]]) extends Datum

  object Array {
    implicit val datumArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
  }

}
