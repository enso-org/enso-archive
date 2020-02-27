package org.enso.languageserver.data.buffer
import scala.collection.mutable.ArrayBuffer

object StringUtils {
  def getLines(string: String): (List[String], Option[String]) = {
    var substringStart = 0
    var curIdx         = 0

    val lines: ArrayBuffer[String] = new ArrayBuffer[String]()

    def pushString(): Unit = {
      lines.addOne(string.substring(substringStart, curIdx + 1))
      substringStart = curIdx + 1
    }

    while (curIdx < string.length) {
      val currentChar = string.charAt(curIdx)
      if (currentChar == '\r') {
        if (curIdx + 1 < string.length && string.charAt(curIdx + 1) == '\n') {
          curIdx += 1
        }
        pushString()
      } else if (currentChar == '\n') {
        pushString()
      }
      curIdx += 1
    }

    val lastLine =
      if (substringStart < string.length)
        Some(string.substring(substringStart, string.length))
      else None

    (lines.toList, lastLine)
  }

  private def endsInWindowsStyleNewline(string: String): Boolean = {
    string.length > 1 &&
    string.charAt(string.length - 2) == '\r' &&
    string.charAt(string.length - 1) == '\n'
  }

  private def endsInSimpleNewline(string: String): Boolean = {
    string.length > 0 && string.charAt(string.length - 1) == '\n'
  }

  def endsInNewline(string: String): Boolean = {
    endsInSimpleNewline(string) || endsInWindowsStyleNewline(string)
  }

  def stripNewline(string: String): String = {
    if (endsInWindowsStyleNewline(string)) {
      string.substring(0, string.length - 2)
    } else if (endsInSimpleNewline(string)) {
      string.substring(0, string.length - 1)
    } else string
  }
}
