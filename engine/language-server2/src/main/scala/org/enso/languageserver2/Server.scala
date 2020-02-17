package org.enso.languageserver2
import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  PoisonPill,
  Props,
  Stash
}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.enso.jsonrpcserver.MessageHandler
import org.enso.jsonrpcserver.MessageHandler.{
  Connected,
  Disconnected,
  WebMessage
}
import org.enso.languageserver2.Protocol.{
  Client,
  ClientId,
  Config,
  Connect,
  Disconnect,
  Env,
  Initialize
}

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory
import io.circe.Encoder

import scala.concurrent.Await
import scala.io.StdIn
import org.enso.jsonrpcserver.{Protocol => JsonProtocol}

object Protocol {
  type ClientId = UUID
  case class Initialize(config: Config)
  case class Connect(client: Client)
  case class Disconnect(clientId: ClientId)

  case class Config(contentRoots: List[File], languagePath: List[File])

  case class Client(id: ClientId, actor: ActorRef, capabilities: List[Unit])

  case class Env(clients: Map[ClientId, Client]) {
    def addClient(client: Client): Env =
      copy(clients = clients + (client.id -> client))

    def removeClient(clientId: ClientId): Env =
      copy(clients = clients - clientId)
  }

  object Env {
    def empty: Env = Env(Map())
  }
}

class Server extends Actor with Stash with ActorLogging {
  override def receive: Receive = {
    case Initialize(config) =>
      log.debug("Language Server initialized.")
      unstashAll()
      context.become(initialized(config))
    case _ => stash()
  }

  def initialized(config: Config, env: Env = Env.empty): Receive = {
    case Connect(client) =>
      log.debug("Client connected [{}].", client.id)
      context.become(initialized(config, env.addClient(client)))
    case Disconnect(clientId) =>
      log.debug("Client disconnected [{}].", clientId)
      context.become(initialized(config, env.removeClient(clientId)))
  }
}

case class WsConnect(webActor: ActorRef)

class Client(val id: ClientId, val server: ActorRef)
    extends Actor
    with Stash
    with ActorLogging {
  override def receive: Receive = {
    case WsConnect(webActor) =>
      log.debug("WebSocket connected.")
      unstashAll()
      context.become(connected(webActor))
    case _ => stash()
  }

  def connected(webActor: ActorRef): Receive = {
    case Disconnected =>
      log.debug("WebSocket disconnected.")
      server ! Disconnect(id)
      context.stop(self)
  }
}

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()

    println(ConfigFactory.load())

    val dummyProtocol: JsonProtocol = JsonProtocol(Set(), Map(), Map(), Map(), {
      json =>
        ???
    })

    val serverActor: ActorRef = system.actorOf(Props(new Server))

    serverActor ! Initialize(Config(List(), List()))

    def newUser(): Flow[Message, Message, NotUsed] = {
      val clientId    = UUID.randomUUID()
      val clientActor = system.actorOf(Props(new Client(clientId, serverActor)))

      val messageHandler =
        system.actorOf(Props(new MessageHandler(dummyProtocol, clientActor)))
      clientActor ! WsConnect(messageHandler)

      serverActor ! Connect(Client(clientId, clientActor, List()))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message]
          .map {
            case TextMessage.Strict(text) =>
              MessageHandler.WebMessage(text)
          }
          .to(
            Sink.actorRef[MessageHandler.WebMessage](
              messageHandler,
              Disconnected, { x: Any =>
                Disconnected
              }
            )
          )

      val outgoingMessages: Source[Message, NotUsed] =
        Source
          .actorRef[MessageHandler.WebMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            messageHandler ! Connected(outActor)
            NotUsed
          }
          .map(
            (outMsg: WebMessage) => TextMessage(outMsg.message)
          )

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
