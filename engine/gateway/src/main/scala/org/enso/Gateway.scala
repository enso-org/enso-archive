package org.enso

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import org.enso.gateway.{Protocol, Server}

case class Gateway(languageServer: ActorRef)(implicit
                                             val system: ActorSystem,
                                             val materializer: ActorMaterializer
) extends Server with Protocol with Actor {
  import Protocol._

  override def handleRequestOrNotification(requestOrNotification: RequestOrNotification): Option[Response] = {
    requestOrNotification match {
      case initialize(jsonrpc, id, method, params) =>
        println(s"jsonrpc=$jsonrpc, id=$id, method=$method, params=$params")
        languageServer ! LanguageServer.Initialize()
        Some(Response.result(
          id = Some(id),
          result = InitializeResult(
            capabilities = ServerCapabilities(),
            serverInfo = Some(ServerInfo(name = "Enso Language Server", version = Some("1.0")))
          )
        ))

      case initialized(jsonrpc, method, params) =>
        println(s"jsonrpc=$jsonrpc, method=$method, params=$params")
        languageServer ! LanguageServer.Initialized()
        None

      case _ =>
        throw new Exception(s"unimplemented request or notification: $requestOrNotification")
    }
  }

  override def receive: Receive = {
    case Gateway.Start(host, port) => run(Server.Config(host, port))
    case s@"Initialize received" => println(s)
    case s@"Initialized received" => println(s)
  }
}

object Gateway {
  case class Start(host: String, port: Int)

  def props(languageServer: ActorRef)(implicit
                                      system: ActorSystem,
                                      materializer: ActorMaterializer
  ): Props = Props(new Gateway(languageServer))
}