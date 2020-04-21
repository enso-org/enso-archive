package org.enso.languageserver.websocket.binary
import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.RemoteAddress
import org.enso.languageserver.http.server.ConnectionControllerFactory
import org.enso.languageserver.protocol.binary.BinaryConnectionController

class BaseBinaryServerTest extends BinaryServerTestKit {

  @volatile
  protected var lastConnectionController: ActorRef = _

  override def connectionControllerFactory: ConnectionControllerFactory = {
    (maybeIp: Option[RemoteAddress.IP]) =>
      {
        val controller =
          system.actorOf(Props(new BinaryConnectionController(maybeIp)))
        lastConnectionController = controller
        controller
      }
  }

}
