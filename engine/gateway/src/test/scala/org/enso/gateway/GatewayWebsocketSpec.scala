package org.enso.gateway

import akka.actor.ActorRef
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.util.ByteString
import org.enso
import org.enso.{Gateway, LanguageServer}

import scala.concurrent.duration._
import org.enso.gateway.Server
import org.enso.gateway.protocol.JsonRpcController
import org.enso.languageserver.SetGateway

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

  "Websocket" should {

    "reply properly" in {
      // tests:
      val wsClient = WSProbe()

      // WS creates a WebSocket request for testing
      WS("/", wsClient.flow) ~> server.route ~>
      check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true

        // manually run a WS conversation
        wsClient.sendMessage(
          TextMessage.Strict(
            "{\"jsonrpc\":\"2.0\", \"method\":\"initialize\", \"id\": 10, \"params\" : {\"capabilities\" : {}}}"
          )
        )
        //        val msg = wsClient.expectMessage
        //        println(s"msg=$msg")
        wsClient.expectMessage(
          "{\"jsonrpc\":\"2.0\",\"id\":10,\"result\":{\"capabilities\":{\"textDocumentSync\":{\"openClose\":true,\"change\":1,\"willSaveWaitUntil\":true,\"didSave\":true}},\"serverInfo\":{\"name\":\"Enso Language Server\",\"version\":\"1.0\"}}}"
        )
        //        wsClient.expectMessage("AAAAAAAA")
        //
        //        wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
        //        wsClient.expectNoMessage(100.millis)
        //
        //        wsClient.sendMessage("John")
        //        wsClient.expectMessage("Hello John!")
        //        wsClient.expectMessage("AAAAAAAA")

        //        wsClient.sendCompletion()
        //        wsClient.expectCompletion()
      }
    }

  }

  server.shutdown()
}
