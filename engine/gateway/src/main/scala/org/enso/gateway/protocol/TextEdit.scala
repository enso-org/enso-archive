package org.enso.gateway.protocol

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class TextEdit(
  range: TextRange,
  newText: String
)
object TextEdit {
  implicit val textEditDecoder: Decoder[TextEdit] =
    deriveDecoder
  implicit val textEditEncoder: Encoder[TextEdit] =
    deriveEncoder
}
