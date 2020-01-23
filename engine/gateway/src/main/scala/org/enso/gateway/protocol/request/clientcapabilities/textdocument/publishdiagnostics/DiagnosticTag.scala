package org.enso.gateway.protocol.request.clientcapabilities.textdocument.publishdiagnostics

import io.circe.Decoder

sealed abstract class DiagnosticTag(value: Int)

object DiagnosticTag {
  private val unnecessary          = 1
  private val deprecated           = 2
  private val invalidDiagnosticTag = "Invalid DiagnosticTag"

  case object Unnecessary extends DiagnosticTag(unnecessary)

  case object Deprecated extends DiagnosticTag(deprecated)

  implicit val diagnosticTagDecoder: Decoder[DiagnosticTag] =
    Decoder.decodeInt.emap {
      case `unnecessary` => Right(Unnecessary)
      case `deprecated`  => Right(Deprecated)
      case _             => Left(invalidDiagnosticTag)
    }
}
