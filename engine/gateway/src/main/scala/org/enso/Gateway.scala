package org.enso

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import org.enso.gateway.protocol.response.Result.{InitializeResult, NullResult}
import org.enso.gateway.protocol.response.result.servercapabilities.TextDocumentSync
import org.enso.gateway.protocol.{Id, Notifications, Requests, Response}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}

/** The gateway component talks directly to clients using protocol messages,
  * and then handles these messages by talking to the language server.
  *
  * @param languageServer [[ActorRef]] of [[LanguageServer]] actor.
  */
class Gateway(languageServer: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case Requests.Initialize(id, params) =>
      val msg = "Gateway: Initialize received"
      log.info(msg)
      languageServer ! languageserver.Requests.Initialize(
        id.toLsp,
        sender()
      )

    case languageserver.RequestReceived.Initialize(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Initialize received"
      log.info(msg)
      replyTo ! Response.result(
        id = Some(Id.fromLsp(id)),
        result = InitializeResult(
          capabilities = ServerCapabilities(),
          serverInfo   = Some(serverInfo)
        )
      )

    case Requests.Shutdown(id, _) =>
      val msg = "Gateway: Shutdown received"
      log.info(msg)
      languageServer ! languageserver.Requests.Shutdown(id.toLsp, sender())

    case languageserver.RequestReceived.Shutdown(id, replyTo) =>
      val msg = "Gateway: RequestReceived.Shutdown received"
      log.info(msg)
      replyTo ! Response.result(
        id     = Some(Id.fromLsp(id)),
        result = NullResult
      )

    case Notifications.Initialized(_) =>
      val msg = "Gateway: Initialized received"
      log.info(msg)
      languageServer ! languageserver.Notifications.Initialized

    case languageserver.NotificationReceived.Initialized =>
      val msg = "Gateway: NotificationReceived.Initialized received"
      log.info(msg)

    case Notifications.Exit(_) =>
      val msg = "Gateway: Exit received"
      log.info(msg)
      languageServer ! languageserver.Notifications.Exit

    case languageserver.NotificationReceived.Exit =>
      val msg = "Gateway: NotificationReceived.Exit received"
      log.info(msg)

    case requestOrNotification =>
      val err = "Gateway: unimplemented request or notification: " +
        requestOrNotification
      log.error(err)
  }

  private val serverInfo: ServerInfo = {
    val gatewayPath               = "gateway"
    val languageServerPath        = "languageServer"
    val languageServerNamePath    = "name"
    val languageServerVersionPath = "version"
    val gatewayConfig             = ConfigFactory.load.getConfig(gatewayPath)
    val languageServerConfig      = gatewayConfig.getConfig(languageServerPath)
    val name                      = languageServerConfig.getString(languageServerNamePath)
    val version                   = languageServerConfig.getString(languageServerVersionPath)
    ServerInfo(name, Some(version))
  }
}
object Gateway {
  def props(languageServer: ActorRef): Props =
    Props(new Gateway(languageServer))
}
