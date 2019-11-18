package org.enso.syntax.text

/** This object contains view patterns that allow matching on the parser AST for
  * more sophisticated constructrs.
  *
  * These view patterns are implemented as custom unapply methods that only
  * return [[Some]] when more complex conditions are met.
  */
object View {}
