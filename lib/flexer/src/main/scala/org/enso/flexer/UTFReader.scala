package org.enso.flexer

import java.io._
import java.nio.charset.StandardCharsets

/**  Fast UTF8 reader and preprocessor.
  *  It uses unboxed byte buffer under the hood,
  *  deals correctly with variable length UTF chars
  *  and replaces \r(\n) with \n and \t with 4 spaces.
  */
class UTFReader(input: DataInputStream) {
  import UTFReader._

  val buffer   = new Array[Byte](BUFFERSIZE + 10)
  var offset   = 0
  var length   = 0

  def this(input: InputStream) = this(new DataInputStream(input))
  def this(file: File)         = this(new FileInputStream(file))
  def this(input: String) =
    this(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))

  private var lastChar = ' '.toByte

  private def read() = {
    offset = 0
    while (offset < BUFFERSIZE && readChar()) Unit
    for (_ <- 1 until charLength(lastChar)) {
      buffer(offset) = input.read().toByte
      offset += 1
    }
    length = offset
    offset = 0
  }

  private def readChar(): Boolean = {
    val char = input.read()
    if (char == -1)
      return false
    if (lastChar == '\r' && char != '\n') {
      buffer(offset) = '\n'
      offset += 1
    }
    lastChar = char.toByte
    lastChar match {
      case '\t' =>
        for (_ <- 1 to 4) {
          buffer(offset) = ' '
          offset += 1
        }
      case '\r' =>
      case char =>
        buffer(offset) = char
        offset += 1
    }
    true
  }

  read()

  def charLength(char: Byte): Int = ~char >> 4 match {
    case 0     => 4
    case 1     => 3
    case 2 | 3 => 2
    case _     => 1
  }


  def nextChar(): Int = {
    if (offset >= length) {
      if (length < BUFFERSIZE)
        return '\u0000'
      else
        read()
    }
    var char = buffer(offset).toInt
    offset += 1
    for (_ <- 1 until charLength(char.toByte)) {
      char = char << 8 | buffer(offset)
      offset += 1
    }
    char
  }

  override def toString(): String = {
    val string = new java.lang.StringBuilder()
    while ({
      val char = nextChar()
      string.appendCodePoint(char)
      char != '\u0000'
    }) Unit
    string.toString
  }

}

object UTFReader {

  val BUFFERSIZE = 30

}