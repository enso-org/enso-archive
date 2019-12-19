package org.enso.interpreter.test

import java.io.{ByteArrayOutputStream, StringReader}

import com.oracle.truffle.api.instrumentation.EventBinding
import org.enso.interpreter.Constants
import org.graalvm.polyglot.{Context, Source, Value}
import org.enso.interpreter.instrument.ReplDebuggerInstrument
import org.enso.interpreter.test.CodeLocationsTestInstrument.LocationsEventListener
import org.scalatest.{Assertions, FlatSpec, Matchers}

trait InterpreterRunner {
  case class LocationsInstrumenter(instrument: CodeLocationsTestInstrument) {
    var bindings: List[EventBinding[LocationsEventListener]] = List()

    def assertNodeExists(start: Int, length: Int, kind: Class[_]): Unit =
      bindings ::= instrument.bindTo(start, length, kind)

    def verifyResults(): Unit = {
      bindings.foreach { binding =>
        val listener = binding.getElement
        if (!listener.isSuccessful) {
          Assertions.fail(
            s"Node of type ${listener.getType.getSimpleName} at position ${listener.getStart} with length ${listener.getLength} was not found."
          )
        }
      }
    }

    def close(): Unit = {
      bindings.foreach(_.dispose)
    }
  }

  implicit class RichValue(value: Value) {
    def call(l: Long*): Value =
      InterpreterException.rethrowPolyglot(
        value.execute(l.map(_.asInstanceOf[AnyRef]): _*)
      )
  }
  val output = new ByteArrayOutputStream()
  val ctx    = Context.newBuilder(Constants.LANGUAGE_ID).out(output).build()

  def withLocationsInstrumenter(test: LocationsInstrumenter => Unit): Unit = {
    val instrument = ctx.getEngine.getInstruments
      .get(CodeLocationsTestInstrument.INSTRUMENT_ID)
      .lookup(classOf[CodeLocationsTestInstrument])
    val instrumenter = LocationsInstrumenter(instrument)
    test(instrumenter)
    instrumenter.verifyResults()
    instrumenter.close()
  }

  def evalGeneric(
    code: String,
    mimeType: String,
    doCall: Boolean = true
  ): Value = {
    output.reset()

    val source = Source
      .newBuilder(Constants.LANGUAGE_ID, new StringReader(code), null)
      .mimeType(mimeType)
      .build()

    val module    = InterpreterException.rethrowPolyglot(ctx.eval(source))
    val assocCons = module.getMember("associated_constructor")
    val mainFun   = module.invokeMember("get_method", assocCons, "main")
//    mainFun
    if (doCall) {
      InterpreterException.rethrowPolyglot(mainFun.execute(assocCons))
    } else {
      mainFun
    }
  }

  def eval(code: String, doCall: Boolean = true): Value = {
    evalGeneric(code, Constants.ANONYMOUS_MIME_TYPE, doCall)
  }

  def consumeOut: List[String] = {
    val result = output.toString
    output.reset()
    result.lines.toList
  }

  def getReplInstrument: ReplDebuggerInstrument = {
    ctx.getEngine.getInstruments
      .get(ReplDebuggerInstrument.INSTRUMENT_ID)
      .lookup(classOf[ReplDebuggerInstrument])
  }

  // For Enso raw text blocks inside scala multiline strings
  val rawTQ = "\"\"\""
}

trait InterpreterTest
    extends FlatSpec
    with Matchers
    with InterpreterRunner
    with ValueEquality
