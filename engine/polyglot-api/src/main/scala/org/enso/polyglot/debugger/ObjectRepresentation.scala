package org.enso.polyglot.debugger

// TODO [RW] document
class ObjectRepresentation(val representation: protocol.ObjectRepresentation)
    extends AnyVal {
  override def toString: String = representation.representation()
}
