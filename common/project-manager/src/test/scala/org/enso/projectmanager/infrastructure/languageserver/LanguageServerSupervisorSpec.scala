package org.enso.projectmanager.infrastructure.languageserver

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.miguno.akka.testing.VirtualTime
import org.enso.languageserver.boot.LifecycleComponent.ComponentRestarted
import org.enso.languageserver.boot.{LanguageServerConfig, LifecycleComponent}
import org.enso.projectmanager.boot.configuration.SupervisionConfig
import org.enso.projectmanager.infrastructure.http.AkkaBasedWebSocketConnectionFactory
import org.enso.projectmanager.infrastructure.languageserver.ProgrammableWebSocketServer.{
  Reject,
  ReplyWith
}
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.must.Matchers

import scala.concurrent.Future
import scala.concurrent.duration._

class LanguageServerSupervisorSpec
    extends TestKit(ActorSystem("LanguageServerSupervisorSpec"))
    with ImplicitSender
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar
    with Eventually
    with IntegrationPatience {

  "A language supervisor" should "monitor language server by sending ping requests on regular basis" in new TestCtx {
    //given
    val probe = TestProbe()
    server.withBehaviour {
      case ping @ PingMatcher(requestId) =>
        probe.ref ! ping
        ReplyWith(
          s"""{ "jsonrpc": "2.0", "id": "$requestId", "result": null }"""
        )
    }
    //when
    virtualTime.advance(testInitialDelay)
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

  it should "restart server when pong message doesn't arrive on time" in new TestCtx {
    //given
    when(serverComponent.restart())
      .thenReturn(Future.successful(ComponentRestarted))
    val probe               = TestProbe()
    @volatile var pingCount = 0
    server.withBehaviour {
      case ping @ PingMatcher(requestId) =>
        probe.ref ! ping
        pingCount += 1
        if (pingCount == 5) {
          Reject
        } else {
          ReplyWith(
            s"""{ "jsonrpc": "2.0", "id": "$requestId", "result": null }"""
          )
        }
    }
    //when
    virtualTime.advance(testInitialDelay)
    (1 to 4).foreach { _ =>
      verifyNoInteractions(serverComponent)
      probe.expectMsgPF() { case PingMatcher(_) => () }
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
      probe.expectNoMessage()
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
    }
    probe.expectMsgPF() { case PingMatcher(_) => () }
    virtualTime.advance(testHeartbeatTimeout)
    virtualTime.scheduler.tick()
    eventually {
      verify(serverComponent, times(1)).restart()
    }
    virtualTime.advance(testInitialDelay)
    (1 to 10).foreach { _ =>
      verifyNoMoreInteractions(serverComponent)
      probe.expectMsgPF() { case PingMatcher(_) => () }
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
      probe.expectNoMessage()
      virtualTime.advance(testHeartbeatInterval / 2)
      virtualTime.scheduler.tick()
    }
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

    val testInitialDelay = 5.seconds

    val testHeartbeatInterval = 10.seconds

    val testHeartbeatTimeout = 7.seconds

    val server = new ProgrammableWebSocketServer(testHost, testPort)
    server.start()

    val serverConfig =
      LanguageServerConfig(testHost, testPort, UUID.randomUUID(), "/tmp")

    val supervisionConfig =
      SupervisionConfig(
        testInitialDelay,
        testHeartbeatInterval,
        testHeartbeatTimeout,
        3,
        2.seconds
      )

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
