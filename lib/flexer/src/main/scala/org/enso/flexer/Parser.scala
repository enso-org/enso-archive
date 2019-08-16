package org.enso.flexer

import org.enso.Logger
import org.enso.flexer.debug.Escape
import org.enso.flexer.spec.Macro

import scala.collection.mutable

trait Parser[T] {
  import Parser._

  var reader: ParserReader = _

  var runStatus    = State.Status.Exit.OK
  val stateDefs    = new Array[Int => Int](256)
  val logger       = new Logger()
  var currentMatch = ""

  def getResult(): Option[T]

  def run(input: ParserReader): Result[T] = {
    reader = input
    reader.nextChar()

    while (state.runCurrent() == State.Status.Exit.OK) Unit

    val value: Result.Value[T] = getResult() match {
      case None => Result.Failure(None)
      case Some(result) =>
        if (reader.offset >= reader.length) Result.Success(result)
        else if (runStatus == State.Status.Exit.FAIL)
          Result.Failure(Some(result))
        else Result.Partial(result)
    }
    Result(reader.offset, value)
  }

  final def rewind(): Unit = logger.trace {
    reader.rewind(reader.offset - reader.result.length)
  }

  final def rewindThenCall(rule: () => Unit): Int = logger.trace {
    reader.rewind(reader.lastRuleOffset)
    state.call(rule)
  }

  val ROOT = state.current

  //// State management ////

  // FIXME: This is a hack. Without it sbt crashes and needs to be completely
  //        cleaned to compile again.
  val state = _state
  final object _state {

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
      reader.result.setLength(0)
      while (State.valid(status)) {
        logger.log(
          s"Step (${cstate.ix}:$status) ${Escape.str(reader.currentStr)}(${reader.charCode})"
        )
        status = nextState(status)
        if (State.valid(status))
          reader.charCode = reader.nextChar()
      }
      runStatus = status
      status
    }

    def call(rule: () => Unit): State.Status.Exit = {
      currentMatch = reader.result.toString
      rule()
      State.Status.Exit.OK
    }

  }
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
