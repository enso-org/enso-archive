package org.enso.gateway.protocol.response.error

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * [[org.enso.gateway.protocol.response.Error]] data
  */
sealed trait Data

object Data {
  implicit val dataDecoder: Decoder[Data] = List[Decoder[Data]](
    Decoder[Number].widen,
    Decoder[Boolean].widen,
    Decoder[Array].widen,
    Decoder[String].widen,
    Decoder[InitializeData].widen
  ).reduceLeft(_ or _)

  case class String(value: Predef.String) extends Data

  object String {
    implicit val dataStringEncoder: Encoder[String] = deriveUnwrappedEncoder
    implicit val dataStringDecoder: Decoder[String] = deriveUnwrappedDecoder
  }

  case class Number(value: Int) extends Data

  object Number {
    implicit val dataNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val dataNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  case class Boolean(value: scala.Boolean) extends Data

  object Boolean {
    implicit val dataBooleanEncoder: Encoder[Boolean] = deriveUnwrappedEncoder
    implicit val dataBooleanDecoder: Decoder[Boolean] = deriveUnwrappedDecoder
  }

  case class Array(value: Seq[Option[Datum]]) extends Data

  object Array {
    implicit val dataArrayEncoder: Encoder[Array] = deriveUnwrappedEncoder
    implicit val dataArrayDecoder: Decoder[Array] = deriveUnwrappedDecoder
  }

  /**
    * [[org.enso.gateway.protocol.Initialize]] data
    */
  case class InitializeData(
    retry: Boolean
  ) extends Data

  object InitializeData {
    implicit val initializeDataEncoder: Encoder[InitializeData] = deriveEncoder
    implicit val initializeDataDecoder: Decoder[InitializeData] = deriveDecoder
  }

}
