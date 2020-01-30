package org.enso.gateway.protocol.response.error

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import cats.syntax.functor._

/** Data of [[org.enso.gateway.protocol.response.ResponseError]]. */
sealed trait Data

object Data {
  implicit val dataEncoder: Encoder[Data] = Encoder.instance {
    case text: Text                     => text.asJson
    case number: Number                 => number.asJson
    case boolean: Bool                  => boolean.asJson
    case array: Array                   => array.asJson
    case parseData: ParseData           => parseData.asJson
    case initializeData: InitializeData => initializeData.asJson
  }

  implicit val paramsDecoder: Decoder[Data] = List[Decoder[Data]](
    Decoder[Text].widen,
    Decoder[Number].widen,
    Decoder[Bool].widen,
    Decoder[Array].widen,
    Decoder[ParseData].widen,
    Decoder[InitializeData].widen
  ).reduceLeft(_ or _)

  /** A string data. */
  case class Text(value: String) extends Data

  object Text {
    implicit val dataStringEncoder: Encoder[Text] = deriveUnwrappedEncoder
    implicit val dataStringDecoder: Decoder[Text] = deriveUnwrappedDecoder
  }

  /** A number data. */
  case class Number(value: Int) extends Data
  object Number {
    implicit val dataNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val dataNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /** A boolean data. */
  case class Bool(value: Boolean) extends Data
  object Bool {
    implicit val dataBooleanEncoder: Encoder[Bool] = deriveUnwrappedEncoder
    implicit val dataBooleanDecoder: Decoder[Bool] = deriveUnwrappedDecoder
  }

  /** An array data. */
  case class Array(value: Seq[Option[Datum]]) extends Data
  object Array {
    implicit val dataArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
    implicit val dataArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
  }

  /** Data of [[org.enso.gateway.protocol.response.ResponseError.ParseError]].
    */
  case class ParseData(
    json: String,
    circeMessage: String
  ) extends Data
  object ParseData {
    implicit val parseDataEncoder: Encoder[ParseData] = deriveEncoder
    implicit val parseDataDecoder: Decoder[ParseData] = deriveDecoder
  }

  /** Data of [[org.enso.gateway.protocol.Requests.Initialize]]. */
  case class InitializeData(
    retry: Boolean
  ) extends Data
  object InitializeData {
    implicit val initializeDataEncoder: Encoder[InitializeData] = deriveEncoder
    implicit val initializeDataDecoder: Decoder[InitializeData] = deriveDecoder
  }
}
