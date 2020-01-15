package org.enso.gateway.protocol.request

import io.circe.Decoder
import org.enso.gateway.protocol.Id
import io.circe.generic.semiauto.deriveDecoder

case class IdHolder(id: Id)

object IdHolder {
  implicit val idHolderDecoder: Decoder[IdHolder] =
    deriveDecoder
}
