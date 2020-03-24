package org.enso.languageserver.filemanager

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder}

import scala.util.Try

/**
  * A representation of UTC time.
  *
  * @param time time in UTC zone
  */
case class UTCDateTime(time: OffsetDateTime)

object UTCDateTime {

  private val IsoDateTime =
    DateTimeFormatter.ISO_ZONED_DATE_TIME

  implicit val decoder: Decoder[UTCDateTime] =
    Decoder.instance { cursor =>
      cursor.as[String].flatMap { string =>
        Try(OffsetDateTime.parse(string, IsoDateTime)).toEither
          .map(UTCDateTime(_))
          .left
          .map(DecodingFailure.fromThrowable(_, cursor.history))
      }
    }

  implicit val encoder: Encoder[UTCDateTime] =
    Encoder.instance[UTCDateTime](_.time.format(IsoDateTime).asJson)
}
