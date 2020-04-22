package org.enso.languageserver.http.server

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import org.enso.languageserver.http.server.BinaryWebSocketServer.Config
import org.enso.languageserver.http.server.WebSocketControlProtocol.{
  CloseConnection,
  ConnectionClosed,
  ConnectionFailed,
  OutboundStreamEstablished
}
import org.enso.languageserver.util.binary.{
  BinaryDecoder,
  BinaryEncoder,
  DecodingFailure
}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * A web socket server realizing binary protocol.
  *
  * @param decoder an inbound packets decoder
  * @param encoder an outbound packets encoder
  * @param factory creates connection front controller per a single connection
  * @param config a server config
  * @param system an actor system
  * @param materializer an actor materializer
  * @tparam A a type of messages sent to connection controller
  * @tparam B a type of messages received from connection controller
  */
class BinaryWebSocketServer[A, B](
  decoder: BinaryDecoder[A],
  encoder: BinaryEncoder[B],
  factory: ConnectionControllerFactory,
  config: Config = Config.default
)(
  implicit val system: ActorSystem,
  implicit val materializer: Materializer
) extends LazyLogging {

  implicit val ec: ExecutionContext = system.dispatcher

  private val route: Route =
    extractClientIP { ip =>
      path(config.path) {
        get { handleWebSocketMessages(newConnection(ip.toIP)) }
      }
    }

  /**
    * Binds this server instance to a given port and interface, allowing
    * future connections.
    *
    * @param interface the interface to bind to.
    * @param port the port to bind to.
    * @return a server binding object.
    */
  def bind(interface: String, port: Int): Future[Http.ServerBinding] =
    Http().bindAndHandle(route, interface, port)

  private def newConnection(
    ip: Option[RemoteAddress.IP]
  ): Flow[Message, Message, NotUsed] = {

    val frontController = factory.createController(ip)

    val inboundFlow  = createInboundFlow(frontController, ip)
    val outboundFlow = createOutboundFlow(frontController)

    Flow.fromSinkAndSource(inboundFlow, outboundFlow)
  }

  private def createOutboundFlow(
    frontController: ActorRef
  ): Source[Message, NotUsed] = {
    Source
      .actorRef[B](
        completionMatcher,
        PartialFunction.empty,
        config.outgoingBufferSize,
        OverflowStrategy.fail
      )
      .mapMaterializedValue { outActor =>
        frontController ! OutboundStreamEstablished(outActor)
        NotUsed
      }
      .map { (outMsg: B) =>
        val bytes = encoder.encode(outMsg)
        BinaryMessage(ByteString.apply(bytes))
      }
  }

  private def completionMatcher: PartialFunction[Any, CompletionStrategy] = {
    case CloseConnection => CompletionStrategy.draining
  }

  private def createInboundFlow(
    frontController: ActorRef,
    ip: Option[RemoteAddress.IP]
  ): Sink[Message, NotUsed] = {
    Flow[Message]
      .mapConcat[BinaryMessage] {
        case msg: TextMessage =>
          logger.warn(
            s"Received text message $msg inside binary protocol [$ip]"
          )
          Nil

        case msg: BinaryMessage =>
          msg :: Nil
      }
      .mapAsync(Runtime.getRuntime.availableProcessors()) {
        _.toStrict(config.lazyMessageTimeout)
      }
      .map { binaryMsg =>
        val bytes = binaryMsg.data.asByteBuffer
        decoder.decode(bytes)
      }
      .to {
        Sink.actorRef[Either[DecodingFailure, A]](
          frontController,
          ConnectionClosed,
          ConnectionFailed
        )
      }
  }

}

object BinaryWebSocketServer {

  /**
    * A configuration object for properties of the JsonRpcServer.
    *
    * @param outgoingBufferSize the number of messages buffered internally
    *                           if the downstream connection is lagging behind.
    * @param lazyMessageTimeout the timeout for downloading the whole of a lazy
    *                           stream message from the user.
    * @param path the http path that the server listen to.
    */
  case class Config(
    outgoingBufferSize: Int,
    lazyMessageTimeout: FiniteDuration,
    path: String = ""
  )

  case object Config {

    /**
      * Creates a default instance of [[Config]].
      *
      * @return a default config.
      */
    def default: Config =
      Config(outgoingBufferSize = 10, lazyMessageTimeout = 10.seconds)
  }
}