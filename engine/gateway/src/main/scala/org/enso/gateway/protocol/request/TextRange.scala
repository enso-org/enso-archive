package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class TextRange(
  start: Position,
  end: Position
)

object TextRange {
  implicit val textRangeDecoder: Decoder[TextRange] =
    deriveDecoder
}
