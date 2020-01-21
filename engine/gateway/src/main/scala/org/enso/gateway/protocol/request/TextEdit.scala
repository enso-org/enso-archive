package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class TextEdit(
  range: TextRange,
  newText: String
)

object TextEdit {
  implicit val textEditDecoder: Decoder[TextEdit] =
    deriveDecoder
}
