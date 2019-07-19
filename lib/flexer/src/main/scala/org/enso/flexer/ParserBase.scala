package org.enso.flexer

import org.enso.Logger

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.runtime.universe.Tree

trait ParserBase[T] {
  import java.io.Reader
  import java.io.StringReader

  import scala.collection.mutable.StringBuilder

  var sreader: Reader     = null
  val buffer: Array[Char] = new Array(ParserBase.BUFFERSIZE)
  var bufferLen: Int      = 1

  var offset: Int    = 0
  var retreatN: Int  = 0
  val eofChar: Char  = '\0'
  val etxChar: Char  = '\3'
  var codePoint: Int = etxChar.toInt

  var matchBuilder = new StringBuilder(64)
  var currentMatch = ""

  val groups: Array[() => Int] = new Array(256)

  val logger = new Logger()

  def getResult(): Option[T]

  def run(input: String): Result[T] = {
    initialize()
    sreader = new StringReader(input)
    val numRead = sreader.read(buffer, 1, buffer.length - 1)
    bufferLen = if (numRead == -1) 1 else numRead + 1
    codePoint = getNextCodePoint()
    var r = -1
    while (r == -1) {
      r = step()
    }

    getResult() match {
      case None => InternalFailure(offset)
      case Some(result) =>
        if (offset >= bufferLen) Success(result, offset)
        else if (r == -2) Failure(result, offset)
        else Partial(result, offset)
    }
  }

  // Group management

  var groupStack: List[Int]                   = Nil
  val groupLabelMap: mutable.Map[Int, String] = mutable.Map()
  var group: Int                              = 0

  def insideOfGroup(g: Group): Boolean = insideOfGroup(g.groupIx)
  def insideOfGroup(g: Int):   Boolean = group == g || groupStack.contains(g)

  def beginGroup(group: Group): Unit =
    beginGroup(group.groupIx)

  def beginGroup(g: Int): Unit = {
    logger.log(s"Begin ${groupLabel(g)}")
    groupStack +:= group
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

  var groupsx = new ArrayBuffer[Group]()

  def defineGroup(label: String = "unnamed", finish: => Unit = {}): Group = {
    val groupIndex = groupsx.length
    val group      = new Group(groupIndex, () => finish)
    groupsx.append(group)
    groupLabelMap += (groupIndex -> label)
    group
  }

  def getGroup(g: Int): Group = groupsx(g)

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
  def getNextCodePoint(): Int = {
    if (offset >= bufferLen)
      return etxChar
    offset += charSize
    if (offset >= ParserBase.BUFFERSIZE - 1) {
      val keep = Math.max(retreatN, currentMatch.length) + 1
      for (i <- 1 to keep) buffer(keep - i) = buffer(bufferLen - i)
      val numRead = sreader.read(buffer, keep, buffer.length - keep)
      if (numRead == -1)
        return eofChar
      offset    = keep - (if (offset == ParserBase.BUFFERSIZE) 0 else 1)
      bufferLen = keep + numRead
    } else if (offset == bufferLen)
      return eofChar
    logger.log(s"Next char '${escapeChar(buffer(offset))}'")
    Character.codePointAt(buffer, offset)
  }

  final def rewind(): Unit = logger.trace {
    rewind(currentMatch.length)
  }

  final def rewind(i: Int): Unit = logger.trace {
    offset -= i + 1
    currentChar = getNextCodePoint()
  }

  final def retreat(): Unit = logger.trace {
    logger.log(s"RETREAT $retreatN")
    offset -= retreatN
    retreatN  = 0
    codePoint = getNextCodePoint()
  }

  final def charSize: Int =
    if (buffer(offset).isHighSurrogate) 2 else 1

  def debugGeneratedOutput: Seq[Tree] = groupsx.map(_.generate())
}

object ParserBase {
  val BUFFERSIZE = 16384
}
