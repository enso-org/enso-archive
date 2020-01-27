package org.enso.gateway

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.enso.{Gateway, LanguageServer}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.enso.gateway.TestMessage.{
  ApplyWorkspaceEdit,
  Initialize,
  Shutdown,
  WillSaveTextDocumentWaitUntil
}
import org.enso.gateway.TestNotification.{Exit, Initialized}

class GatewayMessageSpec()
    extends TestKit(ActorSystem("GatewayMessageSpec"))
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  private val languageServerActorName = "testingLanguageServer"
  private val gatewayActorName        = "testingGateway"
  private val languageServer: ActorRef =
    system.actorOf(LanguageServer.props(null), languageServerActorName)
  protected val gateway: ActorRef =
    system.actorOf(Gateway.props(languageServer), gatewayActorName)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "Gateway" must {
    "properly handle init/shutdown workflow" in {
      sendInitializeAndInitialized()
      expectNoMessage()

      sendShutdownAndExit()
      expectNoMessage()
    }

    "properly handle request ApplyWorkspaceEdit" in {
      sendInitializeAndInitialized()

      gateway ! ApplyWorkspaceEdit.request
      expectMsg(ApplyWorkspaceEdit.response)

      sendShutdownAndExit()
    }

    "properly handle request WillSaveTextDocumentWaitUntil" in {
      sendInitializeAndInitialized()

      gateway ! WillSaveTextDocumentWaitUntil.request
      expectMsg(WillSaveTextDocumentWaitUntil.response)

      sendShutdownAndExit()
    }
  }

  private def sendInitializeAndInitialized(): Unit = {
    gateway ! Initialize.request
    expectMsg(Initialize.response)

    gateway ! Initialized.notification
  }

  private def sendShutdownAndExit(): Unit = {
    gateway ! Shutdown.request
    expectMsg(Shutdown.response)

    gateway ! Exit.notification
  }
}
