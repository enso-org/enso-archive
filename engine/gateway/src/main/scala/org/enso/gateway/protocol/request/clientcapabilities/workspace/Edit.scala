package org.enso.gateway.protocol.request.clientcapabilities.workspace

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.clientcapabilities.workspace.edit.{
  FailureHandlingKind,
  ResourceOperationKind
}

/** Capabilities specific to `WorkspaceEdit`s. */
case class Edit(
  documentChanges: Option[Boolean],
  resourceOperations: Option[Seq[ResourceOperationKind]],
  failureHandling: Option[FailureHandlingKind]
)
object Edit {
  implicit val clientCapabilitiesWorkspaceEditDecoder: Decoder[Edit] =
    deriveDecoder
  implicit val clientCapabilitiesWorkspaceEditEncoder: Encoder[Edit] =
    deriveEncoder
}
