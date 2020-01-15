package org.enso.gateway

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.testkit.TestKit
import akka.{Done, NotUsed}
import org.enso.gateway.TestJsons.{Initialize, WrongJsonrpc, WrongMethod}
import org.enso.gateway.Server.Config
import org.enso.{Gateway, LanguageServer}
import org.scalatest.{
  Assertion,
  BeforeAndAfterAll,
  GivenWhenThen,
  Matchers,
  WordSpecLike
}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class GatewaySpec(_system: ActorSystem)
    extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll
    with GivenWhenThen {

  def this() = this(ActorSystem("GatewaySpec"))

  implicit private val materializer: ActorMaterializer =
    ActorMaterializer.create(system)

  override def beforeAll: Unit = {
    val languageServerActorName = "languageServer"
    val gatewayActorName        = "gateway"
    val languageServer: ActorRef =
      system.actorOf(LanguageServer.props(null), languageServerActorName)
    val gateway: ActorRef =
      system.actorOf(Gateway.props(languageServer), gatewayActorName)

    val protocol = new Protocol(gateway)
    val server   = new Server(protocol)
    server.run()
  }

  override def afterAll: Unit = {
    shutdown(system)
  }

  "Gateway" should {
    "reply with a proper response to request with initialize method" in {
      checkRequestResponse(Initialize)
    }

    "reply with a proper error to request with wrong jsonrpc" in {
      checkRequestResponse(WrongJsonrpc)
    }

    "reply with a proper error to request with wrong method" in {
      checkRequestResponse(WrongMethod)
    }
  }

  private def checkRequestResponse(testJsons: TestJsons): Assertion = {
    Given("server replies with responses to requests")
    val source: Source[Message, NotUsed] =
      Source.single(TextMessage(testJsons.request))
    var actualResponseJson: Option[String] = None
    val sink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict =>
        actualResponseJson = Some(message.text)
      case _ =>
    }

    When("server receives response")
    waitResponse(source, sink)

    Then("actual response should correspond to expected")
    assert(actualResponseJson === Some(testJsons.expectedResponse))
  }

  private def waitResponse(
    source: Source[Message, NotUsed],
    sink: Sink[Message, Future[Done]]
  ): Done = {
    val flow: Flow[Message, Message, Future[Done]] =
      Flow.fromSinkAndSourceMat(sink, source)(Keep.left)

    val (_, doneFuture) = Http()
      .singleWebSocketRequest(WebSocketRequest(Config.addressString), flow)

    val timeout: FiniteDuration = 10 seconds

    Await.result(doneFuture, timeout)
  }
}
