package org.enso.gateway.protocol.codec

import io.circe.Encoder
import io.circe.syntax._
import org.enso.gateway.protocol.{Message, RequestOrNotification, Response}

object MessageEncoder {
  val instance: Encoder[Message] = Encoder.instance {
    case requestOrNotification: RequestOrNotification =>
      requestOrNotification.asJson
    case response: Response => response.asJson
  }
}
