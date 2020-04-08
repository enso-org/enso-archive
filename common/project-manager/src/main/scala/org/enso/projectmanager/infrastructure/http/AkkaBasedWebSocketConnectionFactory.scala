package org.enso.projectmanager.infrastructure.http
import akka.actor.ActorSystem
import org.enso.projectmanager.data.SocketData

/**
  * A factory of Akka-based web socket connections.
  */
class AkkaBasedWebSocketConnectionFactory(implicit system: ActorSystem)
    extends WebSocketConnectionFactory {

  /** @inheritdoc **/
  override def createConnection(socket: SocketData): WebSocketConnection =
    new AkkaBasedWebSocketConnection(s"ws://${socket.host}:${socket.port}")

}
