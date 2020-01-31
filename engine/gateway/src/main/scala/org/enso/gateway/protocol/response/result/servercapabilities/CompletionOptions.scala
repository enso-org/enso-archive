package org.enso.gateway.protocol.response.result.servercapabilities

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/** Server capability to provide completion support. */
case class CompletionOptions(
  triggerCharacters: Option[Seq[String]]   = None,
  allCommitCharacters: Option[Seq[String]] = None,
  resolveProvider: Option[Boolean]         = None,
  workDoneProgress: Option[Boolean]        = None
)
object CompletionOptions {
  implicit val serverCapabilitiesCompletionOptionsEncoder
    : Encoder[CompletionOptions] = deriveEncoder
  implicit val serverCapabilitiesCompletionOptionsDecoder
    : Decoder[CompletionOptions] = deriveDecoder
}
