package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class TextRange(
  start: Position,
  end: Position
)

object TextRange {
  implicit val textRangeDecoder: Decoder[TextRange] =
    deriveDecoder
  implicit val textRangeEncoder: Encoder[TextRange] =
    deriveEncoder
}
