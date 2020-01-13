package org.enso.gateway.protocol.response.error

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveUnwrappedEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._

/**
  * Data of [[org.enso.gateway.protocol.response.Error]]
  */
sealed trait Data

object Data {
  implicit val dataEncoder: Encoder[Data] = Encoder.instance {
    case string: String                 => string.asJson
    case number: Number                 => number.asJson
    case boolean: Boolean               => boolean.asJson
    case array: Array                   => array.asJson
    case initializeData: InitializeData => initializeData.asJson
  }

  /**
    * A string data
    */
  case class String(value: Predef.String) extends Data

  object String {
    implicit val dataStringEncoder: Encoder[String] = deriveUnwrappedEncoder
  }

  /**
    * A number data
    */
  case class Number(value: Int) extends Data

  object Number {
    implicit val dataNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
  }

  /**
    * A boolean data
    */
  case class Boolean(value: scala.Boolean) extends Data

  object Boolean {
    implicit val dataBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
  }

  /**
    * An array data
    */
  case class Array(value: Seq[Option[Datum]]) extends Data

  object Array {
    implicit val dataArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
  }

  /**
    * Data of [[org.enso.gateway.protocol.Requests.Initialize]]
    */
  case class InitializeData(
    retry: Boolean
  ) extends Data

  object InitializeData {
    implicit val initializeDataEncoder: Encoder[InitializeData] = deriveEncoder
  }
}
