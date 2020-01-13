package org.enso

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.enso.gateway.protocol.response.Result.InitializeResult
import org.enso.gateway.protocol.{RequestOrNotification, Response}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import org.enso.gateway.{Protocol, Server}
import org.enso.gateway.protocol.{Notifications, Requests}

/**
  * The gateway component talks directly to clients using protocol messages,
  * and then handles these messages by talking to the language server.
  */
case class Gateway(languageServer: ActorRef)(
  implicit
  val system: ActorSystem,
  val materializer: ActorMaterializer
) extends Server
    with Protocol {

  /**
    * @param requestOrNotification A request or notification
    * @return A response for requests, no response for notification
    */
  override def reply(
    requestOrNotification: RequestOrNotification
  ): Option[Response] = {
    requestOrNotification match {
      case req @ Requests.Initialize(_, _) =>
        languageServer ! LanguageServer.Initialize()

        Some(
          req.response(
            InitializeResult(ServerCapabilities(), Some(serverInfo))
          )
        )

      case Notifications.Initialized(_) =>
        languageServer ! LanguageServer.Initialized()
        None

      case _ =>
        val err =
          s"unimplemented request or notification: $requestOrNotification"
        throw new Exception(err)
    }
  }

  override def receive: Receive = {
    case Gateway.Start() => run()
    case received @ LanguageServer.InitializeReceived() =>
      log.info(received.toString)
    case received @ LanguageServer.InitializedReceived() =>
      log.info(received.toString)
  }

  private val serverInfo: ServerInfo = {
    val gatewayPath      = "gateway"
    val serverInfoPath   = "serverInfo"
    val lspNamePath      = "lspName"
    val lspVersionPath   = "lspVersion"
    val gatewayConfig    = ConfigFactory.load.getConfig(gatewayPath)
    val serverInfoConfig = gatewayConfig.getConfig(serverInfoPath)
    val name             = serverInfoConfig.getString(lspNamePath)
    val version          = serverInfoConfig.getString(lspVersionPath)
    ServerInfo(name, Some(version))
  }
}

object Gateway {

  /**
    * Message starting Gateway Akka service
    */
  case class Start()

  def props(languageServer: ActorRef)(
    implicit
    system: ActorSystem,
    materializer: ActorMaterializer
  ): Props = Props(new Gateway(languageServer))
}
