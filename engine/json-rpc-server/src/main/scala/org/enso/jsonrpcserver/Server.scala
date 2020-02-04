package org.enso.jsonrpcserver
import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.enso.jsonrpcserver.Server.MessageHandler.{
  Connected,
  IncomingMessage,
  OutgoingMessage
}

import scala.concurrent.Await
import scala.io.StdIn

object Server {

  object Bare {
    import io.circe.generic.auto._

    type Id = String
    sealed trait BareMessage

    case class Notification(method: String, params: Json)      extends BareMessage
    case class Request(method: String, id: Id, params: Json)   extends BareMessage
    case class ResponseResult(id: Option[Id], result: Json)    extends BareMessage
    case class ResponseError(id: Option[Id], error: ErrorData) extends BareMessage

    case class ErrorData(code: Int, message: String, data: Json)

    implicit val decoder: Decoder[BareMessage] = new Decoder[BareMessage] {
      val expectedNotificationKeys: Set[String] =
        Set("jsonrpc", "method", "params")
      val expectedRequestKeys: Set[String] =
        Set("jsonrpc", "method", "params", "id")
      val expectedResponseResultKeys: Set[String] =
        Set("jsonrpc", "id", "result")
      val expectedResponseErrorKeys: Set[String] = Set("jsonrpc", "id", "error")

      override def apply(c: HCursor): Result[BareMessage] = {
        val jsonRpcValid = c
            .downField("jsonrpc")
            .as[String] == Right("2.0")
        if (!jsonRpcValid) {
          return Left(
            DecodingFailure("Invalid JSON RPC version manifest.", List())
          )
        }
        val fields = c.keys.getOrElse(List()).toSet
        if (fields == expectedRequestKeys) {
          c.as[Request]
        } else if (fields == expectedNotificationKeys) {
          c.as[Notification]
        } else if (fields == expectedResponseResultKeys) {
          c.as[ResponseResult]
        } else if (fields == expectedResponseErrorKeys) {
          c.as[ResponseError]
        } else {
          Left(DecodingFailure("Malformed JSON RPC message.", List()))
        }
      }
    }

    def parse(a: String): Option[BareMessage] = {
      val foo = io.circe.parser.parse(a)
      println(foo)
      val bar = foo.toOption.map(_.as[BareMessage])
      println(bar)
      bar.flatMap(_.toOption)
    }

  }

  trait Method {
    def name: String
    type RequestParams
    implicit val requestParamsDecoder: Decoder[RequestParams]
    type ResponseParams
    implicit val responseParamsDecoder: Decoder[ResponseParams]
  }

  class MessageHandler extends Actor with Stash {

    override def receive: Receive = {
      case Connected(outConnection) =>
        unstashAll()
        context.become(established(outConnection))
      case _ => stash()
    }

    def established(outConnection: ActorRef): Receive = {
      case IncomingMessage(msg) =>
        println(Bare.parse(msg))
        outConnection ! OutgoingMessage(msg)
    }
  }

  object MessageHandler {
    case class IncomingMessage(message: String)
    case class OutgoingMessage(message: String)
    case class Connected(outConnection: ActorRef)
  }

  def main(args: Array[String]): Unit = {
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()

//    val chatRoom = system.actorOf(Props(new ChatRoom), "chat")

    def newUser(): Flow[Message, Message, NotUsed] = {
      // new connection - new user actor
      val userActor = system.actorOf(Props(new MessageHandler()))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message]
          .map {
            // transform websocket message to domain message
            case TextMessage.Strict(text) =>
              MessageHandler.IncomingMessage(text)
          }
          .to(
            Sink.actorRef[MessageHandler.IncomingMessage](userActor, PoisonPill)
          )

      val outgoingMessages: Source[Message, NotUsed] =
        Source
          .actorRef[MessageHandler.OutgoingMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            userActor ! Connected(outActor)
            NotUsed
          }
          .map(
            // transform domain message to web socket message
            (outMsg: OutgoingMessage) => TextMessage(outMsg.message)
          )

      // then combine both to a flow
      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    val route =
      path("") {
        get {
          handleWebSocketMessages(newUser())
        }
      }

    val binding =
      Await.result(Http().bindAndHandle(route, "127.0.0.1", 1234), 3.seconds)

    // the rest of the sample code will go here
    println("Started server at 127.0.0.1:8080, press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}
