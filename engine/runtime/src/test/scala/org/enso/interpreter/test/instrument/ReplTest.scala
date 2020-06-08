package org.enso.interpreter.test.instrument

import java.nio.ByteBuffer

import org.enso.interpreter.test.InterpreterRunner
import org.enso.polyglot.debugger.{
  DebugServerInfo,
  Debugger,
  SessionStartNotification
}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.enso.polyglot.{debugger, LanguageInfo, PolyglotContext}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ReplRunner extends InterpreterRunner {
  var endPoint: MessageEndpoint = _
  var messageQueue
    : List[debugger.Response] = List() // TODO probably need a better message handler

  override val ctx = Context
    .newBuilder(LanguageInfo.ID)
    .allowExperimentalOptions(true)
    .allowAllAccess(true)
    .option(DebugServerInfo.ENABLE_OPTION, "true")
    .out(output)
    .err(err)
    .in(in)
    .serverTransport { (uri, peer) =>
      println(uri)
      if (uri.toString == DebugServerInfo.URI) {
        endPoint = peer
        new MessageEndpoint {
          override def sendText(text: String): Unit = {}

          override def sendBinary(data: ByteBuffer): Unit =
            Debugger.deserializeResponse(data) match {
              case Some(response) =>
                println(s"Got $response")
                messageQueue ++= Seq(response)
              case None =>
                throw new RuntimeException(
                  "Failed to deserialize response from the debugger"
                )
            }

          override def sendPing(data: ByteBuffer): Unit = {}

          override def sendPong(data: ByteBuffer): Unit = {}

          override def sendClose(): Unit = {}
        }
      } else null
    }
    .build()

  override lazy val executionContext = new PolyglotContext(ctx)

  println("Done123?")
}

class ReplTest extends AnyWordSpec with Matchers with ReplRunner {
  "Repl" should {
    "send start notification" in {
      val code =
        """
          |main = Debug.breakpoint
          |""".stripMargin
      eval(code)

      messageQueue should contain(SessionStartNotification)
    }
  }
}
