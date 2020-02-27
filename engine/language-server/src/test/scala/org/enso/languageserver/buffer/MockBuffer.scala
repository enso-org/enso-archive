package org.enso.languageserver.buffer
import org.enso.languageserver.data.buffer.StringUtils

case class MockBuffer(lines: List[String])

case object MockBuffer {
  def apply(str: String): MockBuffer = {
    val (lines, lastLine) = StringUtils.getLines(str)
    MockBuffer(lines ++ lastLine)
  }
}
