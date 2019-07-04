package org.enso.syntax

import org.enso.Logger

import scala.collection.mutable
import scala.language.implicitConversions

object Flexer {

  trait Result[T] {
    def map[S](fn: T => S): Result[S]
  }

  final case class Success[T](value: T) extends Result[T] {
    def map[S](fn: T => S) = Success(fn(value))
  }
  final case class Partial[T](value: T, offset: Int) extends Result[T] {
    def map[S](fn: T => S) = Partial(fn(value), offset)
  }
  final case class Failure[T](value: T, offset: Int) extends Result[T] {
    def map[S](fn: T => S) = Failure(fn(value), offset)
  }

  abstract class ParserBase[T] {

    import java.io.{Reader, StringReader}

    import scala.collection.mutable.StringBuilder

    val BUFFERSIZE = 16384

    var sreader: Reader     = null
    val buffer: Array[Char] = new Array(BUFFERSIZE)
    var bufferLen: Int      = 0

    var offset: Int       = -1
    var codePoint: Int    = 0
    var currentChar: Char = '\0'
    val eofChar: Char     = '\3' // ETX

    var matchBuilder = new StringBuilder(64)
    var currentMatch = ""

    val groups: Array[() => Int] = new Array(256)

    val logger = new Logger()

    var result: T

    def run(input: String): Result[T] = {
      initialize()
      sreader = new StringReader(input)
      val numRead = sreader.read(buffer, 0, buffer.length)
      bufferLen   = numRead
      currentChar = getNextChar
      var r = -1
      while (r == -1) {
        r = step()
      }

      if (offset >= bufferLen) Success(result)
      else if (r == -2) Failure(result, offset)
      else Partial(result, offset)
    }

    // Group management

    var groupStack: List[Int]                   = Nil
    val groupLabelMap: mutable.Map[Int, String] = mutable.Map()
    var group: Int                              = 0

//    def beginGroup(group: Group[_]): Unit =
//      throw new Exception("Should never be used without desugaring.")

    def beginGroup(g: Int): Unit = {
      logger.log(s"Begin ${groupLabel(g)}")
      groupStack :+= group
      group = g
    }

    def endGroup(): Unit = {
      val oldGroup = group
      group      = groupStack.head
      groupStack = groupStack.tail
      logger.log(s"End ${groupLabel(oldGroup)}, back to ${groupLabel(group)}")
    }

    def step(): Int = {
      groups(group)()
    }

    def initialize(): Unit

    def groupLabel(index: Int): String =
      groupLabelMap.get(index) match {
        case None        => "unnamed"
        case Some(label) => label
      }

    def escapeChar(ch: Char): String = ch match {
      case '\b' => "\\b"
      case '\t' => "\\t"
      case '\n' => "\\n"
      case '\f' => "\\f"
      case '\r' => "\\r"
      case '"'  => "\\\""
      case '\'' => "\\\'"
      case '\\' => "\\\\"
      case _ =>
        if (ch.isControl) "\\0" + Integer.toOctalString(ch.toInt)
        else String.valueOf(ch)
    }

    def getNextChar: Char = {
      offset += 1
      val nextChar = if (offset >= bufferLen) {
        if (offset == bufferLen) eofChar
        else '\0'
      } else buffer(offset)
      logger.log(s"Next char '${escapeChar(nextChar)}'")
      nextChar
    }
  }
}
