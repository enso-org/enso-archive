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

  // buffer will be unboxed as long as we don't use any fancy scala collection methods on it
  val buffer   = new Array[Byte](BUFFERSIZE + 10)
  val result   = new java.lang.StringBuilder()
  var offset   = 0
  var length   = 0
  var charSize = 0
  var charCode = ENDOFINPUT

  def this(input: InputStream) = this(new DataInputStream(input))
  def this(file: File)         = this(new FileInputStream(file))
  def this(input: String) =
    this(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))

  protected var lastByte = ' '.toByte

  protected def fill() = {
    offset = 0
    while (offset < BUFFERSIZE && readChar()) Unit
    for (_ <- 1 until charLength(lastByte))
      buffer(nextOffset()) = input.read().toByte
    length = offset
    offset = 0
  }

  protected def readChar(): Boolean = {
    val char = input.read()
    if (char == ENDOFINPUT)
      return false
    lastByte             = char.toByte
    buffer(nextOffset()) = lastByte
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
    result.appendCodePoint(if (charCode == ENDOFINPUT) '\0' else charCode)
    if (offset >= length && length >= BUFFERSIZE)
        fill()
    var char = buffer(nextOffset()).toInt
    charSize = charLength(char.toByte)
    for (_ <- 1 until charSize)
      char = char << BYTELENGTH | buffer(nextOffset())
    charCode = char
    charCode
  }

  override def toString(): String = {
    while (nextChar() != ENDOFINPUT) Unit
    result.toString
  }

}

object UTFReader {

  val BYTELENGTH = 8
  val ENDOFINPUT = -1
  val BUFFERSIZE = 30000

  def charLength(char: Byte): Int = ~char >> 4 match {
    case 0     => 4
    case 1     => 3
    case 2 | 3 => 2
    case _     => 1
  }
}

object Main extends App {
  println(new UTFReader("Hello my dear!\n" * 10))
}
