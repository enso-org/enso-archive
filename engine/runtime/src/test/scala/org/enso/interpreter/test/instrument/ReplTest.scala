package org.enso.interpreter.test.instrument

import java.nio.ByteBuffer

import org.enso.interpreter.test.{InterpreterException, InterpreterRunner}
import org.enso.polyglot.debugger.protocol.{
  ExceptionRepresentation,
  ObjectRepresentation
}
import org.enso.polyglot.debugger.{
  DebugServerInfo,
  Debugger,
  DebuggerSessionManagerEndpoint,
  ReplExecutor,
  SessionExitSuccess,
  SessionManager,
  SessionStartNotification
}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.io.MessageEndpoint
import org.enso.polyglot.{debugger, LanguageInfo, PolyglotContext}
import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait ReplRunner extends InterpreterRunner {
  var endPoint: MessageEndpoint = _
  var messageQueue
    : List[debugger.Response] = List() // TODO probably need a better message handler

  class ReplaceableSessionManager extends SessionManager {
    var currentSessionManager: SessionManager = _
    def setSessionManager(manager: SessionManager): Unit =
      currentSessionManager = manager

    override def startSession(executor: ReplExecutor): executor.SessionEnded =
      currentSessionManager.startSession(executor)
  }

  private val sessionManager = new ReplaceableSessionManager

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
        new DebuggerSessionManagerEndpoint(sessionManager, peer)
      } else null
    }
    .build()

  override lazy val executionContext = new PolyglotContext(ctx)

  def setSessionManager(manager: SessionManager): Unit =
    sessionManager.setSessionManager(manager)

  /**
    * Sets as the current session manager, a manager that executes the provided
    * function and finishes the session with a call to executor.exit.
    * The session does not have to (and must not) call executor.exit.
    */
  def wrapSessionManager(session: ReplExecutor => Unit): Unit =
    sessionManager.setSessionManager(new SessionManager {
      override def startSession(
        executor: ReplExecutor
      ): executor.SessionEnded = {
        session(executor)
        executor.exit()
      }
    })
}

class ReplTest
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfter
    with ReplRunner {

  after {
    messageQueue = List()
  }

  "Repl" should {
    "initialize properly" in {
      val code =
        """
          |main = Debug.breakpoint
          |""".stripMargin
      wrapSessionManager(_ => ())
      eval(code)
    }

    "be able to execute arbitrary code in the caller scope" in {
      val code =
        """
          |main =
          |    x = 1
          |    y = 2
          |    Debug.breakpoint
          |""".stripMargin
      var evalResult: Either[ExceptionRepresentation, ObjectRepresentation] =
        null
      wrapSessionManager { executor =>
        evalResult = executor.evaluate("x + y")
        executor.exit()
      }
      eval(code)
      evalResult.fold(_.toString, _.representation()) shouldEqual "3"
    }

    "return the last evaluated value back to normal execution flow" in {
      val code =
        """
          |main =
          |    a = 5
          |    b = 6
          |    c = Debug.breakpoint
          |    c * a
          |""".stripMargin
      wrapSessionManager { executor =>
        executor.evaluate("a + b")
        executor.exit()
      }
      eval(code) shouldEqual 55
    }

    "be able to define its local variables" in {
      val code =
        """
          |main =
          |    x = 10
          |    Debug.breakpoint
          |""".stripMargin
      wrapSessionManager { executor =>
        executor.evaluate("y = x + 1")
        executor.evaluate("z = y * x")
        executor.evaluate("z")
        executor.exit()
      }
      eval(code) shouldEqual 110
    }

    "access and modify monadic state" in {
      val code =
        """
          |main =
          |    State.put 10
          |    Debug.breakpoint
          |    State.get
          |""".stripMargin
      wrapSessionManager { executor =>
        executor.evaluate("x = State.get")
        executor.evaluate("State.put (x + 1)")
        executor.exit()
      }
      eval(code) shouldEqual 11
    }

    "be able to list local variables in its scope" in {
      val code =
        """
          |main =
          |    x = 10
          |    y = 20
          |    z = x + y
          |
          |    Debug.breakpoint
          |""".stripMargin
      var scopeResult: Map[String, ObjectRepresentation] = Map()
      wrapSessionManager { executor =>
        scopeResult = executor.listBindings()
        executor.exit()
      }
      eval(code)
      scopeResult.view.mapValues(_.representation()).toMap shouldEqual Map(
        "this" -> "Test",
        "x"    -> "10",
        "y"    -> "20",
        "z"    -> "30"
      )
    }

    "be able to list bindings it has created" in {
      val code =
        """
          |main =
          |    x = 10
          |    y = 20
          |    z = x + y
          |
          |    Debug.breakpoint
          |""".stripMargin
      var scopeResult: Map[String, ObjectRepresentation] = Map()
      wrapSessionManager { executor =>
        executor.evaluate("x = y + z")
        scopeResult = executor.listBindings()
        executor.exit()
      }
      eval(code)
      scopeResult.view.mapValues(_.representation()).toMap shouldEqual Map(
        "this" -> "Test",
        "x"    -> "50",
        "y"    -> "20",
        "z"    -> "30"
      )
    }
  }
}
