package org.enso.gateway.protocol.codec

import io.circe.Decoder
import org.enso.gateway.protocol.{Message, RequestOrNotification, Response}
import cats.syntax.functor._

object MessageDecoder {
  val instance: Decoder[Message] = List[Decoder[Message]](
    Decoder[RequestOrNotification].widen,
    Decoder[Response].widen
  ).reduceLeft(_ or _)
}
