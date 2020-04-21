package org.enso.languageserver.websocket.binary
import akka.actor.Props
import akka.http.scaladsl.model.RemoteAddress
import org.enso.languageserver.http.server.ConnectionControllerFactory
import org.enso.languageserver.protocol.binary.BinaryConnectionController

class BaseBinaryServerTest extends BinaryServerTestKit {

  override def connectionControllerFactory: ConnectionControllerFactory = {
    (maybeIp: Option[RemoteAddress.IP]) =>
      system.actorOf(Props(new BinaryConnectionController(maybeIp)))
  }

}
