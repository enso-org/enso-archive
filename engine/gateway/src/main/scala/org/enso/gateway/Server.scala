package org.enso.gateway

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import akka.util.Timeout
import org.enso.gateway.protocol.JsonRpcController
import org.enso.gateway.server.Config

import scala.concurrent.{Await, Future}
import scala.util.Failure
import scala.util.Success

/** WebSocket server supporting synchronous request-response protocol.
  *
  * Server when run binds to endpoint and accepts establishing web socket
  * connection for any number of peers.
  * Server replies to each incoming text request with a single text response,
  * no response for notifications.
  * Server accepts a single Text Message from a peer and responds with another
  * Text Message.
  *
  * @param jsonRpcController Encapsulates encoding JSONs and talking to
  *                          [[org.enso.Gateway]].
  * @param config            Server config.
  */
class Server(jsonRpcController: JsonRpcController, config: Config)(
  implicit
  system: ActorSystem,
  materializer: ActorMaterializer
) {

  import system.dispatcher

  implicit private val timeout: Timeout = Timeout(config.timeout)
  private val log: LoggingAdapter       = Logging.getLogger(system, this)

  private var connections: List[TextMessage => Unit] = List()

  def sendToConnections(s: String): Unit =
    connections.foreach(_.apply(TextMessage.Strict(s)))

  private val incoming: Sink[Message, Any] =
    Sink.foreachAsync(parallelism = 1) {
      case tm: TextMessage =>
        val strict = tm.textStream.fold("")(_ + _)
        strict
          .runForeach { input =>
            jsonRpcController
              .getTextOutput(input)
              .foreach(_.foreach(sendToConnections))
          }
          .map(_ => ())
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore).map(_ => ())
    }

  private val outcoming: Source[Message, SourceQueueWithComplete[Message]] =
    Source.queue[Message](16, OverflowStrategy.fail)

  /** Akka stream defining server behavior.
    *
    * Incoming [[TextMessage]]s are replied to.
    *
    * @see [[JsonRpcController.getTextOutput]].
    *      Incoming binary messages are ignored.
    */
  private val handlerFlow: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSourceMat(incoming, outcoming)((_, outcomingMat) => {
      connections ::= outcomingMat.offer
      NotUsed
    })

  /** Server behavior upon receiving HTTP request.
    *
    * As server implements websocket-based protocol, this implementation accepts
    * only GET requests to set up WebSocket connection.
    *
    * The request's URI is not checked.
    */
  val route: Route =
    path(config.route) {
      get {
        handleWebSocketMessages(handlerFlow)
      }
    }

  private val bindingFuture: Future[Http.ServerBinding] =
    Http().bindAndHandle(
      handler   = route,
      interface = config.host,
      port      = config.port
    )

  /** Starts a HTTP server listening at the given endpoint.
    *
    * Function is asynchronous, will return immediately. If the server fails to
    * start, function will exit the process with a non-zero code.
    */
  def run(): Unit = {
    bindingFuture
      .onComplete {
        case Success(_) =>
          val serverOnlineMessage =
            s"Server online at ${config.addressString}"
          val shutDownMessage = "Press ENTER to shut down"
          Seq(
            serverOnlineMessage,
            shutDownMessage
          ).foreach(log.info)
        case Failure(exception) =>
          val err = s"Failed to start server: $exception"
          log.error(err)
          system.terminate()
          System.exit(1)
      }
  }

  /** Stops the HTTP server gracefully. */
  def shutdown(): Future[Http.HttpTerminated] = {
    Await
      .result(bindingFuture, config.bindingTimeout)
      .terminate(hardDeadline = config.hardDeadline)
  }
}
