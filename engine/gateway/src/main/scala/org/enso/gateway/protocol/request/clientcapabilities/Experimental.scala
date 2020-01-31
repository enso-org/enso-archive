package org.enso.gateway.protocol.request.clientcapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}

/** Defines capabilities for experimental features the client supports. */
case class Experimental(value: String) extends AnyVal
object Experimental {
  implicit val clientCapabilitiesExperimentalDecoder: Decoder[Experimental] =
    deriveUnwrappedDecoder
  implicit val clientCapabilitiesExperimentalEncoder: Encoder[Experimental] =
    deriveUnwrappedEncoder
}
