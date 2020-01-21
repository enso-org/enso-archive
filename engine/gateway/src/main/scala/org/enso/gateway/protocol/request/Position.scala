package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Position(line: Int, character: Int)

object Position {
  implicit val positionDecoder: Decoder[Position] =
    deriveDecoder
}
