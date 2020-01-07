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
      "{\"jsonrpc\":\"2.0\",\"id\":10,\"result\":{\"capabilities\":{},\"serverInfo\":{\"name\":\"Enso Language Server\",\"version\":\"1.0\"}}}"

    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer.create(system)
    import system.dispatcher

    val languageServer: ActorRef = system.actorOf(LanguageServer.props(null), "languageServer")
    val gateway       : ActorRef = system.actorOf(Gateway.props(languageServer), "gateway")
    gateway ! Gateway.Start()

    var text: Option[String] = None

    val sink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict =>
        text = Some(message.text)
      case _ =>
    }

    val source: Source[Message, NotUsed] = Source.single(TextMessage(requestJson))

    val flow: Flow[Message, Message, Future[Done]] = Flow.fromSinkAndSourceMat(sink, source)(Keep.left)

    val (_, doneFuture) = Http().singleWebSocketRequest(WebSocketRequest(Config.addressString), flow)

    Await.result(doneFuture, 1 second)

    assert(text.getOrElse("") == expectedResponseJson)

    system.terminate()
  }
}