package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.clientcapabilities.common.SymbolKinds

/** Capabilities specific to the `textDocument/documentSymbol` request. */
case class DocumentSymbol(
  dynamicRegistration: Option[Boolean]               = None,
  symbolKind: Option[SymbolKinds]                    = None,
  hierarchicalDocumentSymbolSupport: Option[Boolean] = None
)
object DocumentSymbol {
  implicit val clientCapabilitiesTextDocumentSymbolDecoder
    : Decoder[DocumentSymbol] = deriveDecoder
  implicit val clientCapabilitiesTextDocumentSymbolEncoder
    : Encoder[DocumentSymbol] = deriveEncoder
}
