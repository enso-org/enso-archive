package org.enso.languageserver.text.editing

import org.enso.languageserver.text.editing.model.Position

sealed trait TextEditValidationFailure
case object EndPositionBeforeStartPosition     extends TextEditValidationFailure
case object NegativeCoordinateInPosition       extends TextEditValidationFailure
case class InvalidPosition(position: Position) extends TextEditValidationFailure
