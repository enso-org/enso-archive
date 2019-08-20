package org.enso.flexer

import java.io._
import java.nio.charset.StandardCharsets

/**  Fast UTF8 reader and preprocessor.
  *  It uses unboxed byte buffer under the hood,
  *  deals correctly with variable length UTF chars
  *  and replaces \r(\n) with \n and \t with 4 spaces.
  */
class ReaderUTF(val input: DataInputStream) {
  import ReaderUTF._

  // buffer will be unboxed as long as we don't use any fancy scala collection methods on it
  val buffer   = new Array[Byte](BUFFERSIZE + 10)
  var offset   = 0
  var length   = 0
  var charSize = 0
  var charCode = ENDOFINPUT

  def this(input: InputStream) = this(new DataInputStream(input))
  def this(file: File)         = this(new FileInputStream(file))
  def this(input: String) =
    this(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))

  protected var lastChar = ' '.toByte

  protected def fill(): Unit = {
    offset = 0
    while (offset < BUFFERSIZE && readChar()) Unit
    for (_ <- 1 until charLength(lastChar))
      buffer(nextOffset()) = input.read().toByte
    length = offset
    offset = 0
  }

  protected def readChar(): Boolean = {
    val char = input.read()
    if (char == ENDOFINPUT)
      return false
    lastChar             = char.toByte
    buffer(nextOffset()) = lastChar
    true
  }

  final protected def nextOffset(): Int = {
    println("offset " + offset)
    val off = offset
    offset += 1
    off
  }

  fill()

  def nextChar(): Int = {
    println("nextChar, offset: " + offset)
    if (offset >= length)
      if (length >= BUFFERSIZE)
        fill()
      else {
        charSize = 0
        charCode = -1
        return charCode
      }
    var char = buffer(nextOffset()).toInt
    charSize = charLength(char.toByte)
    char     = char & charMask(charSize)
    for (_ <- 1 until charSize)
      char = char << UTFBYTESIZE | (buffer(nextOffset()) & charMask(0))
    charCode = char
    charCode
  }

  override def toString(): String = {
    val builder = new java.lang.StringBuilder()
    while (nextChar() != ENDOFINPUT) builder.appendCodePoint(charCode)
    builder.toString
  }

  final def currentStr: String =
    if (charCode < 0) "" else new String(Character.toChars(charCode))

}

object ReaderUTF {

  val ENDOFINPUT  = -1
  val BUFFERSIZE  = 30000
  val UTFBYTESIZE = 6

  /** For more info on UTF decoding look at: https://en.wikipedia.org/wiki/UTF-8 */
  def charLength(char: Byte): Int = ~char >> 4 match {
    case 0     => 4
    case 1     => 3
    case 2 | 3 => 2
    case _     => 1
  }

  def charMask(size: Int): Int = size match {
    case 1 => 127 // 0111 1111
    case 2 => 63  // 0011 1111
    case 3 => 31  // 0001 1111
    case 4 => 15  // 0000 1111
    case _ => 63  // 0011 1111
  }
}
