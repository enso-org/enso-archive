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
  Initialize,
  Shutdown,
  WillSaveTextDocumentWaitUntil
}
import org.enso.languageserver.SendRequestToClient.SendApplyWorkspaceEdit
import org.scalatest.{
  BeforeAndAfterAll,
  BeforeAndAfterEach,
  Matchers,
  WordSpecLike
}
import Definitions._
import org.enso.languageserver.model.ClientCapabilities

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

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsg(
        Response.Initialize(id1, serverInfo, probeRef)
      )

      languageServer ! Initialized
      expectNoMessage(timeout)

      languageServer ! Shutdown(id2, probeRef)
      expectMsg(Response.Shutdown(id2, probeRef))

      languageServer ! Exit
      expectNoMessage(timeout)
    }

    "reply with error to request before initialize" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Shutdown(id1, probeRef)
      expectMsgPF() {
        case ErrorResponse.ServerNotInitialized(`id1`, _, `probeRef`) =>
      }
    }

    "drop notification before initialize" in {
      languageServer ! DidOpenTextDocument
      expectNoMessage(timeout)
    }

    "reply with error to request after initialize but before initialized" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! WillSaveTextDocumentWaitUntil(id2, probeRef)
      expectMsgPF() {
        case ErrorResponse.InvalidRequest(`id2`, _, `probeRef`) =>
      }
    }

    "drop notification after initialize but before initialized" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! DidOpenTextDocument
      expectNoMessage(timeout)
    }

    "properly exit before initialize" in {
      languageServer ! Exit
      expectNoMessage(timeout)
    }

    "reply with error to request after shutdown but before exit" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! Shutdown(id2, probeRef)
      expectMsgClass(classOf[Response.Shutdown])
      languageServer ! WillSaveTextDocumentWaitUntil(id3, probeRef)
      expectMsgPF() {
        case ErrorResponse.InvalidRequest(`id3`, _, `probeRef`) =>
      }
    }

    "drop notification after shutdown but before exit" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! Shutdown(id2, probeRef)
      expectMsgClass(classOf[Response.Shutdown])
      languageServer ! DidOpenTextDocument
      expectNoMessage(timeout)
    }

    "be able to send ApplyWorkspaceEdit request" in {
      val gateway = TestProbe()

      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! SetGateway(gateway.ref)
      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! SendApplyWorkspaceEdit(id2)
      gateway.expectMsg(RequestToClient.ApplyWorkspaceEdit(id2))
    }

    "properly handle ApplyWorkspaceEdit response" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! ResponseFromClient.ApplyWorkspaceEdit(id2)
      expectNoMessage(timeout)
    }

    "properly handle WillSaveTextDocumentWaitUntil request" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! WillSaveTextDocumentWaitUntil(id2, probeRef)
      expectMsg(Response.WillSaveTextDocumentWaitUntil(id2, probeRef))
    }

    "properly handle notifications" in {
      val probe    = TestProbe()
      val probeRef = probe.ref

      languageServer ! Initialize(id1, ClientCapabilities(), replyTo = probeRef)
      expectMsgClass(classOf[Response.Initialize])
      languageServer ! Initialized
      languageServer ! DidOpenTextDocument
      languageServer ! DidChangeTextDocument
      languageServer ! DidSaveTextDocument
      languageServer ! DidCloseTextDocument
      expectNoMessage(timeout)
    }
  }
}
