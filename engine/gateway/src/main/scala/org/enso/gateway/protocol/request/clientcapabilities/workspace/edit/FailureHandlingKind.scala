package org.enso.gateway.protocol.request.clientcapabilities.workspace.edit

import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveEnumerationDecoder

sealed trait FailureHandlingKind

object FailureHandlingKind {

  case object abort extends FailureHandlingKind

  case object transactional extends FailureHandlingKind

  case object undo extends FailureHandlingKind

  case object textOnlyTransactional extends FailureHandlingKind

  implicit val failureHandlingKindDecoder: Decoder[FailureHandlingKind] =
    deriveEnumerationDecoder
}
