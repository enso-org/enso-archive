package org.enso.languageserver.text

import org.enso.languageserver.text.model.Position

sealed trait TextEditValidationFailure
case object EndPositionBeforeStartPosition extends TextEditValidationFailure
case class PositionNotFound(position: Position)
    extends TextEditValidationFailure
