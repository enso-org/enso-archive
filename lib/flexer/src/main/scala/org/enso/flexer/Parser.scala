package org.enso.flexer

import org.enso.Logger
import org.enso.flexer.debug.Escape
import org.enso.flexer.spec.Macro

import scala.collection.mutable

trait Parser[T] {
  import java.io.Reader
  import java.io.StringReader

  import Parser._

  var reader: Reader      = null
  val buffer: Array[Char] = new Array(BUFFER_SIZE)
  var bufferLen: Int      = 0

  var offset: Int          = 0
  var charsToLastRule: Int = 0
  var codePoint: Int       = etxCodePoint

  var matchBuilder = new mutable.StringBuilder(64)
  var currentMatch = ""

  val stateDefs: Array[Int => Int] = new Array(256)

  val logger = new Logger()

  def getResult(): Option[T]

  def run(input: String): Result[T] = {
    reader = new StringReader(input)
    // FIXME: why we have offset 1 here and -1 ?
    val numRead = reader.read(buffer, 1, buffer.length - 1)
    val EOFLen  = 1
    bufferLen = if (numRead == -1) EOFLen else numRead + EOFLen
    codePoint = getNextCodePoint()

    var runResult = State.Status.Exit.OK
    while (runResult == State.Status.Exit.OK) runResult = state.runCurrent()

    val value: Result.Value[T] = getResult() match {
      case None => Result.Failure(None)
      case Some(result) =>
        if (offset >= bufferLen) Result.Success(result)
        else if (runResult == State.Status.Exit.FAIL)
          Result.Failure(Some(result))
        else Result.Partial(result)
    }
    Result(offset, value)
  }

  //// State management ////

  // FIXME: This is a hack. Without it sbt crashes and needs to be completely
  //        cleaned to compile again.
  val state = _state
  object _state {

    var registry = new mutable.ArrayBuffer[State]()

    def define(label: String = "unnamed", finish: => Unit = {}): State = {
      val groupIndex = registry.length
      val newState   = new State(label, groupIndex, () => finish)
      registry.append(newState)
      newState
    }

    var stack: List[State] = Nil
    var current: State     = define("Root")

    def begin(state: State): Unit = {
      logger.log(s"Begin ${state.label}")
      stack +:= current
      current = state
    }

    def end(): Unit = stack match {
      case Nil => logger.err("Trying to end root state")
      case head :: tail =>
        logger.log(s"End ${current.label}, back to ${head.label}")
        current = head
        stack   = tail
    }

    final def endTill(s: State): Unit = logger.trace {
      while (s != state.current) {
        state.current.finish()
        state.end()
      }
    }

    def isInside(state: State): Boolean =
      current == state || stack.contains(state)

    def runCurrent(): Int = {
      val cstate      = state.current
      val nextState   = stateDefs(cstate.ix)
      var status: Int = State.Status.INITIAL
      matchBuilder.setLength(0)
      while (State.valid(status)) {
        logger.log(
          s"Step (${cstate.ix}:$status) ${Escape.str(currentStr)}($codePoint)"
        )
        status = nextState(status)
        if (State.valid(status)) {
          matchBuilder.append(buffer(offset))
          if (buffer(offset).isHighSurrogate)
            matchBuilder.append(buffer(offset + 1))
          codePoint = getNextCodePoint()
        }
      }
      status
    }

  }
  val ROOT = state.current

  // TODO: To be refactored.
  //       There is a lot of hardcoded literals and complex expressions
  //       without proper explanation.
  def getNextCodePoint(): Int = {
    if (offset >= bufferLen)
      return etxCodePoint
    offset += charSize()
    if (offset > BUFFER_SIZE - UTF_CHAR_SIZE) {
      val keepChars = Math.max(charsToLastRule, currentMatch.length) + UTF_CHAR_SIZE - 1
      for (i <- 1 to keepChars) buffer(keepChars - i) = buffer(bufferLen - i)
      val numRead = reader.read(buffer, keepChars, buffer.length - keepChars)
      if (numRead == -1)
        return eofCodePoint
      offset    = keepChars - (BUFFER_SIZE - offset)
      bufferLen = keepChars + numRead
    } else if (offset == bufferLen)
      return eofCodePoint
    Character.codePointAt(buffer, offset)
  }

  final def rewind(): Unit =
    rewind(currentMatch.length)

  final def rewind(i: Int): Unit = logger.trace {
    offset -= i
    codePoint = getNextCodePoint()
  }

  // FIXME: This clearly does more than {rewind(); call(...);}
  //        I believe this name should be much more descriptive as it is
  //        used somewhere in the generated code
  final def rewindThenCall(rule: () => Unit): Int = {
    rewind(charsToLastRule + 1)
    matchBuilder.setLength(matchBuilder.length - charsToLastRule)
    call(rule)
  }

  final def call(rule: () => Unit): State.Status.Exit = {
    currentMatch    = matchBuilder.result()
    charsToLastRule = 0
    rule()
    State.Status.Exit.OK
  }

  final def currentStr: String =
    if (codePoint < 0) "" else new String(Character.toChars(codePoint))

  final def charSize(): Int =
    if (buffer(offset).isHighSurrogate) 2 else 1
}

object Parser {

  val BUFFER_SIZE   = 16384
  val UTF_CHAR_SIZE = 2

  val eofCodePoint: Int = -1
  val etxCodePoint: Int = -2

  object State {
    object Status {
      val INITIAL = 0
      type Exit = Int
      object Exit {
        val OK   = -1
        val FAIL = -2
      }
    }
    def valid(i: Int): Boolean =
      i >= 0
  }

  def compile[T, P](p: P)(implicit ev: P <:< Parser[T]): () => P =
    macro Macro.compileImpl[T, P]

  case class Result[T](offset: Int, value: Result.Value[T]) {
    def map[S](fn: T => S): Result[S] = copy(value = value.map(fn))
  }
  object Result {
    sealed trait Value[T] {
      def map[S](fn: T => S): Value[S]
    }
    final case class Success[T](result: T) extends Value[T] {
      def map[S](fn: T => S) = copy(fn(result))
    }
    final case class Partial[T](result: T) extends Value[T] {
      def map[S](fn: T => S) = copy(fn(result))
    }
    final case class Failure[T](result: Option[T]) extends Value[T] {
      def map[S](fn: T => S) = copy(result.map(fn))
    }
  }

}
