package org.enso.gateway.protocol.request.clientcapabilities.common

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Part of
  * [[org.enso.gateway.protocol.request.clientcapabilities.workspace.WorkspaceSymbol]].
  */
case class SymbolKinds(valueSet: Option[Seq[SymbolKind]]) extends AnyVal

object SymbolKinds {
  implicit val symbolKindsDecoder: Decoder[SymbolKinds] =
    deriveDecoder

  implicit val symbolKindsEncoder: Encoder[SymbolKinds] =
    deriveEncoder
}
