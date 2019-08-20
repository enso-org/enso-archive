package org.enso.flexer

import java.io._
import java.nio.charset.StandardCharsets

import org.enso.flexer.ParserReader._
import org.enso.flexer.ReaderUTF.BUFFERSIZE
import org.enso.flexer.ReaderUTF.ENDOFINPUT

class ParserReader(input: DataInputStream) extends ReaderUTF(input) {

  var lastRuleOffset                  = 0
  var rewinded                        = false
  var result: java.lang.StringBuilder = _

  def this(input: InputStream) = this(new DataInputStream(input))
  def this(file: File)         = this(new FileInputStream(file))
  def this(input: String) =
    this(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))

  final override def init(): Unit = {
    result = new java.lang.StringBuilder() // ugly hack, because scala sucks
    super.init()
  }

  final def charOffset: Int = offset - charSize

  final override def fill(): Unit = {
    val keepchars = result.length()
    lastRuleOffset = lastRuleOffset - (length - keepchars)
    offset         = keepchars
    if (keepchars >= BUFFERSIZE)
      throw new BufferSizeReachedRewindImpossible()
    for (i <- 1 to keepchars)
      buffer(keepchars - i) = buffer(length - i)
    super.fill()
  }

  final override def readChar(): Boolean = {
    val char = input.read()
    if (char == ENDOFINPUT)
      return false
    (lastChar, char) match {
      case ('\r', '\n') =>
      case (_, '\r')    => buffer(nextOffset()) = '\n'
      case (_, '\t')    => for (_ <- 1 to 4) buffer(nextOffset()) = ' '
      case (_, char)    => buffer(nextOffset()) = char.toByte
    }
    lastChar = char.toByte
    true
  }

  final override def nextChar(): Int = {
    rewinded = false
    super.nextChar()
  }

  final def rewind(off: Int): Unit = {
    result.setLength(result.length - (charOffset - off))
    offset = off
    nextChar()
    rewinded = true
  }

}

object ParserReader {

  class BufferSizeReachedRewindImpossible() extends Exception

}
