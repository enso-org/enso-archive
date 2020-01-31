package org.enso.gateway

import akka.actor.ActorRef
import org.scalatest.{Assertion, Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.util.ByteString
import org.enso
import org.enso.{Gateway, LanguageServer}

import scala.concurrent.duration._
import org.enso.gateway.Server
import org.enso.gateway.TestJson.{
  ApplyWorkspaceEdit,
  Exit,
  Initialize,
  Initialized,
  Shutdown,
  WrongJsonrpc
}
import org.enso.gateway.protocol.JsonRpcController
import org.enso.languageserver.SetGateway
import io.circe.parser.parse

class GatewayWebsocketSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest {

  val languageServerActorName = "languageServer"
  val gatewayActorName        = "gateway"
  val languageServer: ActorRef =
    system.actorOf(LanguageServer.props(null), languageServerActorName)
  val gateway: ActorRef =
    system.actorOf(Gateway.props(languageServer), gatewayActorName)
  languageServer ! SetGateway(gateway)

  val jsonRpcController = new JsonRpcController(gateway)
  gateway ! Gateway.SetJsonRpcController(jsonRpcController)
  val config = new enso.gateway.server.Config
  val server =
    new enso.gateway.Server(jsonRpcController, config)
  jsonRpcController.server = server
  server.run()

  private val wsClient = WSProbe()

  private def sendRequestJson(json: TestJson): Unit =
    wsClient.sendMessage(json.request.toString)

  private def expectResponseJson(json: RequestJson): Assertion =
    parse(wsClient.expectMessage match {
      case TextMessage.Strict(s) => s
    }) shouldEqual Right(json.expectedResponse)

  private val timeout = 100.millis

  private def expectNoJson(): Unit =
    wsClient.expectNoMessage(timeout)

  "Gateway" should {
    "properly handle init/shutdown workflow" in {
      WS("/", wsClient.flow) ~> server.route ~>
      check {
        isWebSocketUpgrade shouldEqual true

        sendRequestJson(Initialize)
        expectResponseJson(Initialize)

        sendRequestJson(Initialized)
        expectNoJson()

        sendRequestJson(Shutdown)
        expectResponseJson(Shutdown)

        sendRequestJson(Exit)
        expectNoJson()

        wsClient.sendCompletion()
      }
    }
  }

  server.shutdown()
}
