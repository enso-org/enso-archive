package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Position(line: Int, character: Int)

object Position {
  implicit val positionDecoder: Decoder[Position] =
    deriveDecoder
  implicit val positionEncoder: Encoder[Position] =
    deriveEncoder
}
