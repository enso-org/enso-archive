package org.enso.flexer

import java.io._
import java.nio.charset.StandardCharsets

import org.enso.flexer.ParserReader._
import org.enso.flexer.UTFReader.BUFFERSIZE
import org.enso.flexer.UTFReader.ENDOFINPUT

class ParserReader(input: DataInputStream) extends UTFReader(input) {

  var lastRuleOffset = 0

  def this(input: InputStream) { this(new DataInputStream(input)) }
  def this(file: File) { this(new FileInputStream(file)) }
  def this(input: String) {
    this(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))
  }

  final override def fill(): Unit = {
    val keepchars = result.length()
    lastRuleOffset = lastRuleOffset - (length - keepchars)
    offset         = keepchars
    if (keepchars >= BUFFERSIZE)
      throw new BufferSizeReachedRewindNotPossible()
    for (i <- 1 to keepchars)
      buffer(keepchars - i) = buffer(length - i)
    super.fill()
  }

  final override def readChar(): Boolean = {
    val char = input.read()
    if (char == ENDOFINPUT)
      return false
    if (lastByte == '\r' && char != '\n')
      buffer(nextOffset()) = '\n'
    lastByte = char.toByte
    lastByte match {
      case '\r' =>
      case '\t' => for (_ <- 1 to 4) buffer(nextOffset()) = ' '
      case char => buffer(nextOffset()) = char
    }
    true
  }

  final def rewind(off: Int): Unit = {
    result.setLength(result.length - (offset - off))
    offset   = off
    charCode = nextChar()
  }

  final def currentStr: String =
    if (charCode < 0) "" else new String(Character.toChars(charCode))
}

object ParserReader {

  class BufferSizeReachedRewindNotPossible() extends Exception

}
