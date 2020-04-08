package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.flatspec.{AnyFlatSpec, AnyFlatSpecLike}
import com.miguno.akka.testing.VirtualTime
import org.enso.languageserver.boot.{LanguageServerConfig, LifecycleComponent}
import org.enso.projectmanager.boot.configuration.SupervisionConfig
import org.enso.projectmanager.infrastructure.http.AkkaBasedWebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.ProgrammableWebSocketServer.ReplyWith
import org.mockito.BDDMockito._
import org.mockito.Mockito.{when, _}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers

import scala.concurrent.duration._

class LanguageServerSupervisorSpec
    extends TestKit(ActorSystem("LanguageServerSupervisorSpec"))
    with ImplicitSender
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar {

  "A language supervisor" should "monitor language server by sending ping requests on regular basis" in new TestCtx {
    //given
    val probe = TestProbe()
    server.withBehaviour {
      case ping @ PingMatcher(requestId) =>
        probe.ref ! ping
        ReplyWith(
          s""" { "jsonrpc": "2.0", "id": "$requestId", "result": null } """
        )
    }
    //when
    virtualTime.advance(5.seconds)
    (1 to 10).foreach { _ =>
      probe.expectMsgPF() { case PingMatcher(_) => () }
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
      probe.expectNoMessage()
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
    }
    //then
    `then`(serverComponent.restart()).shouldHaveNoInteractions()
    //teardown
    server.stop()
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait TestCtx {

    val virtualTime = new VirtualTime

    val serverComponent = mock[LifecycleComponent]

    val testHost = "127.0.0.1"

    val testPort = 56523

    val testHeartbeatInterval = 10.seconds

    val server = new ProgrammableWebSocketServer(testHost, testPort)
    server.start()

    val serverConfig =
      LanguageServerConfig(testHost, testPort, UUID.randomUUID(), "/tmp")

    val supervisionConfig =
      SupervisionConfig(testHeartbeatInterval, 10.seconds, 3, 2.seconds)

    val actorUnderTest = system.actorOf(
      LanguageServerSupervisor.props(
        serverConfig,
        serverComponent,
        supervisionConfig,
        new AkkaBasedWebSocketConnectionFactory(),
        virtualTime.scheduler
      )
    )

  }
}
