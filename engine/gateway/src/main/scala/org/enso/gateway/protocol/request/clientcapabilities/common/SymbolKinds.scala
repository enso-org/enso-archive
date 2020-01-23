package org.enso.gateway.protocol.request.clientcapabilities.common

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class SymbolKinds(valueSet: Option[Seq[SymbolKind]]) extends AnyVal

object SymbolKinds {
  implicit val symbolKindsDecoder: Decoder[SymbolKinds] =
    deriveDecoder
}
