package org.enso.languageserver

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.enso.LanguageServer
import org.enso.languageserver.Notification.{
  DidChangeTextDocument,
  DidCloseTextDocument,
  DidOpenTextDocument,
  DidSaveTextDocument,
  Exit,
  Initialized
}
import org.enso.languageserver.Request.{
  ApplyWorkspaceEdit,
  Initialize,
  Shutdown,
  WillSaveTextDocumentWaitUntil
}
import org.scalatest.{
  BeforeAndAfterAll,
  BeforeAndAfterEach,
  Matchers,
  WordSpecLike
}

class LanguageServerSpec
    extends TestKit(ActorSystem("LanguageServerSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  private var testCount: Int           = _
  private var languageServer: ActorRef = _

  override def beforeEach(): Unit = {
    val languageServerActorName = s"testingLanguageServer$testCount"
    testCount += 1
    languageServer =
      system.actorOf(LanguageServer.props(null), languageServerActorName)
  }

  override def afterEach(): Unit = {
    system.stop(languageServer)
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Language Server" must {
    "properly handle init/shutdown workflow" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1     = Id.Number(1)
      val id2     = Id.Number(2)
      val name    = "Enso Language Server"
      val version = "1.0"

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsg(
        Response.Initialize(id1, name, version, probeRef)
      )

      languageServer ! Initialized
      expectNoMessage()

      languageServer ! Shutdown(id2, probeRef)
      expectMsg(Response.Shutdown(id2, probeRef))

      languageServer ! Exit
      expectNoMessage()
    }

    "reply with error to request before initialize" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)

      languageServer ! Shutdown(id1, probeRef)
      expectMsgPF() {
        case ErrorResponse.ServerNotInitialized(`id1`, _, `probeRef`) =>
      }
    }

    "drop notification before initialize" in {
      languageServer ! DidOpenTextDocument
      expectNoMessage()
    }

    "reply with error to request after initialize but before initialized" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)
      val id2 = Id.Number(2)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! ApplyWorkspaceEdit(id2, probeRef)
      expectMsgPF() {
        case ErrorResponse.InvalidRequest(`id2`, _, `probeRef`) =>
      }
    }

    "drop notification after initialize but before initialized" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! DidOpenTextDocument
      expectNoMessage()
    }

    "properly exit before initialize" in {
      languageServer ! Exit
      expectNoMessage()
    }

    "reply with error to request after shutdown but before exit" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)
      val id2 = Id.Number(2)
      val id3 = Id.Number(3)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! Shutdown(id2, probeRef)
      expectMsgClass(classOf[Response.Shutdown])
      languageServer ! ApplyWorkspaceEdit(id3, probeRef)
      expectMsgPF() {
        case ErrorResponse.InvalidRequest(`id3`, _, `probeRef`) =>
      }
    }

    "drop notification after shutdown but before exit" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)
      val id2 = Id.Number(2)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! Shutdown(id2, probeRef)
      expectMsgClass(classOf[Response.Shutdown])
      languageServer ! DidOpenTextDocument
      expectNoMessage()
    }

    "properly handle ApplyWorkspaceEdit request" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)
      val id2 = Id.Number(2)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! ApplyWorkspaceEdit(id2, probeRef)
      expectMsg(Response.ApplyWorkspaceEdit(id2, probeRef))
    }

    "properly handle WillSaveTextDocumentWaitUntil request" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)
      val id2 = Id.Number(2)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! WillSaveTextDocumentWaitUntil(id2, probeRef)
      expectMsg(Response.WillSaveTextDocumentWaitUntil(id2, probeRef))
    }

    "properly handle notifications" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      val id1 = Id.Number(1)

      languageServer ! Initialize(id1, replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! DidOpenTextDocument
      languageServer ! DidChangeTextDocument
      languageServer ! DidSaveTextDocument
      languageServer ! DidCloseTextDocument
      expectNoMessage()
    }

  }
}
