package org.enso.languageserver

import java.io.File
import java.util.UUID

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout
import org.enso.languageserver.JsonRpcApi.CantCompleteRequestError
import org.enso.languageserver.jsonrpc.{
  Error,
  HasParams,
  HasResult,
  MessageHandler,
  Method,
  Notification,
  Protocol,
  Request,
  ResponseError,
  ResponseResult,
  Unused
}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.io.StdIn
import scala.util.{Failure, Success}

object LanguageProtocol {
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
  import LanguageProtocol._

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

object JsonRpcApi {

  case object CantCompleteRequestError
      extends Error(1, "Can't complete request")

  case object AcquireWriteLock extends Method("acquireWriteLock") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ForceReleaseWriteLock extends Method("forceReleaseWriteLock") {
    implicit val hasParams = new HasParams[this.type] {
      type Params = Unused.type
    }
  }

  val protocol: Protocol = Protocol.empty
    .registerRequest(AcquireWriteLock)
    .registerNotification(ForceReleaseWriteLock)
    .registerError(CantCompleteRequestError)

  case class WsConnect(webActor: ActorRef)
}

class Client(val clientId: LanguageProtocol.ClientId, val server: ActorRef)
    extends Actor
    with Stash
    with ActorLogging {
  implicit val timeout: Timeout     = 5.seconds
  implicit val ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case JsonRpcApi.WsConnect(webActor) =>
      log.debug("WebSocket connected.")
      unstashAll()
      context.become(connected(webActor))
    case _ => stash()
  }

  def connected(webActor: ActorRef): Receive = {
    case MessageHandler.Disconnected =>
      log.debug("WebSocket disconnected.")
      server ! LanguageProtocol.Disconnect(clientId)
      context.stop(self)
    case Request(JsonRpcApi.AcquireWriteLock, id, Unused) =>
      (server ? LanguageProtocol.AcquireWriteLock(clientId)).onComplete {
        case Success(LanguageProtocol.WriteLockAcquired) =>
          webActor ! ResponseResult(JsonRpcApi.AcquireWriteLock, id, Unused)
        case Failure(_) =>
          webActor ! ResponseError(Some(id), CantCompleteRequestError)
      }
    case LanguageProtocol.ForceReleaseWriteLock =>
      webActor ! Notification(JsonRpcApi.ForceReleaseWriteLock, Unused)
  }
}

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val serverActor: ActorRef = system.actorOf(Props(new Server))

    serverActor ! LanguageProtocol.Initialize(
      LanguageProtocol.Config(List(), List())
    )

    def newUser(): Flow[Message, Message, NotUsed] = {
      val clientId    = UUID.randomUUID()
      val clientActor = system.actorOf(Props(new Client(clientId, serverActor)))

      val messageHandler =
        system.actorOf(
          Props(new MessageHandler(JsonRpcApi.protocol, clientActor))
        )
      clientActor ! JsonRpcApi.WsConnect(messageHandler)

      serverActor ! LanguageProtocol.Connect(
        clientId,
        LanguageProtocol
          .Client(clientActor, LanguageProtocol.Capabilities.default)
      )

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message]
          .map {
            case TextMessage.Strict(text) =>
              MessageHandler.WebMessage(text)
          }
          .to(
            Sink.actorRef[MessageHandler.WebMessage](
              messageHandler,
              MessageHandler.Disconnected, { _: Any =>
                MessageHandler.Disconnected
              }
            )
          )

      val outgoingMessages: Source[Message, NotUsed] =
        Source
          .actorRef[MessageHandler.WebMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            messageHandler ! MessageHandler.Connected(outActor)
            NotUsed
          }
          .map(
            (outMsg: MessageHandler.WebMessage) => TextMessage(outMsg.message)
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
