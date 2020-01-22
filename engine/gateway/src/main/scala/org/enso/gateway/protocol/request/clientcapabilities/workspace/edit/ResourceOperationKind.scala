package org.enso.gateway.protocol.request.clientcapabilities.workspace.edit

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveEnumerationDecoder

sealed trait ResourceOperationKind

object ResourceOperationKind {

  case object create extends ResourceOperationKind

  case object rename extends ResourceOperationKind

  case object delete extends ResourceOperationKind

  implicit val resourceOperationKindDecoder: Decoder[ResourceOperationKind] =
    deriveEnumerationDecoder
}
