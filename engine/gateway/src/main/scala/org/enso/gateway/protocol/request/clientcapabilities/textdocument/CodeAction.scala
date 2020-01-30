package org.enso.gateway.protocol.request.clientcapabilities.textdocument

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.clientcapabilities.textdocument.codeaction.CodeActionLiteralSupport

/** Capabilities specific to the `textDocument/codeAction` request. */
case class CodeAction(
  dynamicRegistration: Option[Boolean]                       = None,
  codeActionLiteralSupport: Option[CodeActionLiteralSupport] = None,
  isPreferredSupport: Option[Boolean]                        = None
)

object CodeAction {
  implicit val clientCapabilitiesTextDocumentCodeActionDecoder
    : Decoder[CodeAction] = deriveDecoder

  implicit val clientCapabilitiesTextDocumentCodeActionEncoder
    : Encoder[CodeAction] = deriveEncoder
}
