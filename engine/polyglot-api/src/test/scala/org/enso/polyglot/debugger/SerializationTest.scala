package org.enso.polyglot.debugger

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SerializationTest extends AnyFlatSpec with Matchers {
  "Creating and deserializing an EvaluationRequest" should "preserve all information" in {
    val expression = "2 + 2"
    val bytes      = Debugger.createEvaluationRequest(expression)
    val request    = Debugger.deserializeRequest(bytes).get

    request shouldEqual EvaluationRequest(expression)
  }

  "Creating and deserializing a ListBindingsRequest" should "preserve all information" in {
    val bytes   = Debugger.createListBindingsRequest()
    val request = Debugger.deserializeRequest(bytes).get

    request shouldEqual ListBindingsRequest
  }

  "Creating and deserializing an ExitRequest" should "preserve all information" in {
    val bytes   = Debugger.createExitRequest()
    val request = Debugger.deserializeRequest(bytes).get

    request shouldEqual ExitRequest
  }
}
