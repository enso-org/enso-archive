package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.enso.gateway.{Protocol, Server}

/**
  * The gateway component talks directly to clients using protocol messages,
  * and then handles these messages by talking to the language server.
  */
case class Gateway(languageServer: ActorRef)(
  implicit
  val system: ActorSystem,
  val materializer: ActorMaterializer
) extends Server
    with Protocol
    with Actor
    with ActorLogging {

  import Protocol._

  private def loadServerInfo(): ServerInfo = {
    val config           = ConfigFactory.load.getConfig("gateway")
    val serverInfoConfig = config.getConfig("serverInfo")
    val name             = serverInfoConfig.getString("lspName")
    val version          = serverInfoConfig.getString("lspVersion")
    ServerInfo(name, Some(version))
  }

  override def handleRequestOrNotification(
    requestOrNotification: RequestOrNotification
  ): Option[Response] = {
    requestOrNotification match {
      case initialize(_, id, _, _) =>
        languageServer ! LanguageServer.Initialize()

        Some(
          Response.result(
            id = Some(id),
            result = InitializeResult(
              capabilities = ServerCapabilities(),
              serverInfo   = Some(loadServerInfo())
            )
          )
        )

      case initialized(_, _, _) =>
        languageServer ! LanguageServer.Initialized()
        None

      case _ =>
        throw new Exception(
          s"unimplemented request or notification: $requestOrNotification"
        )
    }
  }

  override def receive: Receive = {
    case Gateway.Start()                     => run()
    case LanguageServer.InitializeReceived() => log.info("Initialize received")
    case LanguageServer.InitializedReceived() =>
      log.info("Initialized received")
  }
}

object Gateway {

  case class Start()

  def props(languageServer: ActorRef)(
    implicit
    system: ActorSystem,
    materializer: ActorMaterializer
  ): Props = Props(new Gateway(languageServer))
}
