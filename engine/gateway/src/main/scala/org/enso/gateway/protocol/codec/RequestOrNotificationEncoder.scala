package org.enso.gateway.protocol.codec

import io.circe.Encoder
import io.circe.syntax._
import org.enso.gateway.protocol.{Notification, Request, RequestOrNotification}

object RequestOrNotificationEncoder {
  val instance: Encoder[RequestOrNotification] =
    Encoder.instance {
      case request: Request[p]           => request.asJson
      case notification: Notification[p] => notification.asJson
    }
}
