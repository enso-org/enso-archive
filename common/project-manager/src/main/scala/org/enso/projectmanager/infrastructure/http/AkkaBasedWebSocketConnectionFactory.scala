package org.enso.projectmanager.infrastructure.http
import akka.actor.ActorSystem
import org.enso.projectmanager.data.SocketData

class AkkaBasedWebSocketConnectionFactory(implicit system: ActorSystem)
    extends WebSocketConnectionFactory {

  override def createConnection(socket: SocketData): WebSocketConnection =
    new AkkaBasedWebSocketConnection(s"ws://${socket.host}:${socket.port}")

}
