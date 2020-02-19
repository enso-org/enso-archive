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
  AcquireWriteLock,
  Capabilities,
  Client,
  ClientId,
  Config,
  Connect,
  Disconnect,
  Env,
  ForceReleaseWriteLock,
  Initialize,
  WriteLockAcquired
}

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Await
import scala.io.StdIn
import org.enso.jsonrpcserver.{Protocol => JsonProtocol}

object Protocol {
  type ClientId = UUID
  case class Initialize(config: Config)
  case class Connect(id: ClientId, client: Client)
  case class Disconnect(clientId: ClientId)

  case class Config(contentRoots: List[File], languagePath: List[File])

  case class Capabilities(hasWriteLock: Boolean) {
    def acquireWriteLock: Capabilities = copy(hasWriteLock = true)
    def releaseWriteLock: Capabilities = copy(hasWriteLock = false)
  }

  object Capabilities {
    val default: Capabilities = Capabilities(false)
  }

  case class Client(actor: ActorRef, capabilities: Capabilities) {
    def acquireWriteLock: Client =
      copy(capabilities = capabilities.acquireWriteLock)
    def releaseWriteLock: Client =
      copy(capabilities = capabilities.releaseWriteLock)
    def hasWriteLock: Boolean = capabilities.hasWriteLock
  }

  case class Env(clients: Map[ClientId, Client]) {
    def addClient(id: ClientId, client: Client): Env = {
      copy(clients = clients + (id -> client))
    }

    def removeClient(clientId: ClientId): Env =
      copy(clients = clients - clientId)

    def mapClients(fun: (ClientId, Client) => Client): Env =
      copy(clients = clients.map { case (id, client) => (id, fun(id, client)) })
  }

  object Env {
    def empty: Env = Env(Map())
  }

  case object ForceReleaseWriteLock
  case class AcquireWriteLock(clientId: ClientId)
  case object WriteLockAcquired
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
    case Connect(clientId, client) =>
      log.debug("Client connected [{}].", clientId)
      context.become(initialized(config, env.addClient(clientId, client)))
    case Disconnect(clientId) =>
      log.debug("Client disconnected [{}].", clientId)
      context.become(initialized(config, env.removeClient(clientId)))
    case AcquireWriteLock(clientId) =>
      val newEnv = env.mapClients {
        case (id, client) =>
          if (id == clientId) {
            log.debug("Client {} has acquired the write lock.", clientId)
            sender ! WriteLockAcquired
            client.acquireWriteLock
          } else if (client.hasWriteLock) {
            log.debug("Client {} has lost the write lock.", clientId)
            client.actor ! ForceReleaseWriteLock
            client.releaseWriteLock
          } else client
      }
      context.become(initialized(config, newEnv))
  }
}

case class WsConnect(webActor: ActorRef)

object JsonRpcApi {
  import org.enso.jsonrpcserver._

  object AcquireWriteLock extends Method("acquireWriteLock")
}

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

      serverActor ! Connect(clientId, Client(clientActor, Capabilities.default))

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
