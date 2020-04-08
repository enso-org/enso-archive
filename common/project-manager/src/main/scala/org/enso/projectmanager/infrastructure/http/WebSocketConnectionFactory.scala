package org.enso.projectmanager.infrastructure.http

import org.enso.projectmanager.data.SocketData

trait WebSocketConnectionFactory {

  def createConnection(socket: SocketData): WebSocketConnection

}
