package org.enso

import akka.actor.{ActorRef, ActorSystem}
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model.ws._
import org.enso.gateway.Server.Config
import org.scalatest.FunSuite
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

class GatewayTest extends FunSuite {
  test("gateway should respond properly") {
    val requestJson =
      """|{
         |  "jsonrpc": "2.0",
         |  "method": "initialize",
         |  "id": 10,
         |  "params": {
         |    "capabilities": {}
         |  }
         |}""".stripMargin

    val expectedResponseJson =
      """|{
         |  "jsonrpc": "2.0",
         |  "id": 10,
         |  "result": {
         |    "capabilities": {},
         |    "serverInfo": {
         |      "name": "Enso_Language_Server",
         |      "version": "1.0"
         |    }
         |  }
         |}""".stripMargin
        .filterNot(_.isWhitespace)
        .replace("_", " ")

    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer =
      ActorMaterializer.create(system)

    val languageServerActorName = "languageServer"
    val gatewayActorName        = "gateway"
    val languageServer: ActorRef =
      system.actorOf(LanguageServer.props(null), languageServerActorName)
    val gateway: ActorRef =
      system.actorOf(Gateway.props(languageServer), gatewayActorName)
    gateway ! Gateway.Start()

    val source: Source[Message, NotUsed] =
      Source.single(TextMessage(requestJson))

    var actualResponseJson: Option[String] = None

    val sink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict =>
        actualResponseJson = Some(message.text)
      case _ =>
    }

    val flow: Flow[Message, Message, Future[Done]] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.left)

    val (_, doneFuture) = Http()
      .singleWebSocketRequest(WebSocketRequest(Config.addressString), flow)

    val timeout: Duration = 10 seconds

    Await.result(doneFuture, timeout)

    assert(actualResponseJson === Some(expectedResponseJson))

    system.terminate()
  }
}
