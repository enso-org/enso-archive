package org.enso.gateway.protocol.request

import io.circe.{Decoder, Encoder}
import org.enso.gateway.protocol.{Id}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Helper class necessary for
  * [[org.enso.gateway.protocol.JsonRpcController.getTextOutput()]].
  */
case class IdHolder(id: Id)

object IdHolder {
  implicit val idHolderDecoder: Decoder[IdHolder] =
    deriveDecoder

  implicit val idHolderEncoder: Encoder[IdHolder] =
    deriveEncoder
}
