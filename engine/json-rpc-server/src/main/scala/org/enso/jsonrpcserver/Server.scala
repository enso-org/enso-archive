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
import org.enso.jsonrpcserver.MessageHandler.{Connected, OutgoingMessage}

import scala.concurrent.Await
import scala.io.StdIn

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()

//    val chatRoom = system.actorOf(Props(new ChatRoom), "chat")

    def newUser(): Flow[Message, Message, NotUsed] = {
      // new connection - new user actor
      val userActor =
        system.actorOf(Props(new MessageHandler(???, ???)))

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
