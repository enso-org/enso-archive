package org.enso.gateway.protocol.codec

import io.circe.Decoder
import org.enso.gateway.protocol.request.Params
import org.enso.gateway.protocol.{Id, Notification, Request}

/** Helper object for decoding [[Request]]. */
object RequestDecoder {

  /** Makes Circe decoder for requests.
    *
    * @tparam P Subtype of [[Params]] for a request with specific method.
    * @return The decoder.
    */
  def instance[P <: Params]: Decoder[Request[P]] = cursor => {
    val idCursor = cursor.downField(Field.id)
    for {
      id                 <- Decoder[Id].tryDecode(idCursor)
      notificationFields <- Decoder[Notification[P]].apply(cursor)
    } yield Request[P](
      notificationFields.jsonrpc,
      id,
      notificationFields.method,
      notificationFields.params
    )
  }
}
