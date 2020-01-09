package org.enso.gateway

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.Failure
import scala.util.Success

object Server {

  /** Describes endpoint to which [[Server]] can bind. */
  object Config {
    private val config: Config       = ConfigFactory.load.getConfig("gateway")
    private val serverConfig: Config = config.getConfig("server")
    val host: String                 = serverConfig.getString("host")
    val port: Int                    = serverConfig.getInt("port")
    val route: String                = serverConfig.getString("route")
    val addressString: String        = s"ws://$host:$port"
  }
}

/** WebSocket server supporting synchronous request-response protocol.
  *
  * Server when run binds to endpoint and accepts establishing web socket
  * connection for any number of peers.
  *
  * Server replies to each incoming text request with a single text response, no response for notifications.
  * Server accepts a single Text Message from a peer and responds with another Text Message.
  */
trait Server extends Actor with ActorLogging {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  import system.dispatcher

  /** Generate text reply for given request text message, no reply for notification. */
  def getTextOutput(input: String): Option[String]

  /** Akka stream defining server behavior.
    *
    * Incoming [[TextMessage]]s are replied to (see [[getTextOutput]]).
    * Incoming binary messages are ignored.
    */
  val handlerFlow: Flow[Message, TextMessage.Strict, NotUsed] =
    Flow[Message]
      .flatMapConcat {
        case tm: TextMessage =>
          val strict = tm.textStream.fold("")(_ + _)
          strict.flatMapConcat(
            input =>
              getTextOutput(input) match {
                case Some(output) => Source.single(TextMessage(output))
                case None         => Source.empty
              }
          )
        case bm: BinaryMessage =>
          bm.dataStream.runWith(Sink.ignore)
          Source.empty
      }

  /** Server behavior upon receiving HTTP request.
    *
    * As server implements websocket-based protocol, this implementation accepts
    * only GET requests to set up WebSocket connection.
    *
    * The request's URI is not checked.
    */
  val route: Route =
    path(Server.Config.route) {
      get {
        handleWebSocketMessages(handlerFlow)
      }
    }

  /** Starts a HTTP server listening at the given endpoint.
    *
    * Function is asynchronous, will return immediately. If the server fails to
    * start, function will exit the process with a non-zero code.
    */
  def run(): Unit = {
    val bindingFuture =
      Http().bindAndHandle(
        handler   = route,
        interface = Server.Config.host,
        port      = Server.Config.port
      )

    bindingFuture
      .onComplete {
        case Success(_) =>
          val msg = s"Server online at ${Server.Config.addressString}"
          log.info(msg)
        case Failure(exception) =>
          val msg = s"Failed to start server: $exception"
          log.error(msg)
          system.terminate()
          System.exit(1)
      }
  }
}
