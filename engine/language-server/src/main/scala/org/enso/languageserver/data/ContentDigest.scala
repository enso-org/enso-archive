package org.enso.languageserver.data

/**
  * A digest calculator interface.
  */
trait ContentDigest {

  /**
    * Digests textual content.
    *
    * @param content a textual content
    * @return a digest
    */
  def digest(content: String): String

}
