package org.enso.projectmanager.infrastructure.net
import java.net.InetAddress

import javax.net.ServerSocketFactory

import scala.annotation.tailrec
import scala.util.Random

object Tcp {

  @tailrec
  def findAvailablePort(address: String, minPort: Int, maxPort: Int): Int = {
    val random = Random.nextInt(maxPort - minPort)
    val port   = minPort + random
    if (isPortAvailable(address, port)) {
      port
    } else {
      findAvailablePort(address, minPort, maxPort)
    }
  }

  def isPortAvailable(address: String, port: Int): Boolean =
    try {
      val serverSocket = ServerSocketFactory.getDefault.createServerSocket(
        port,
        1,
        InetAddress.getByName(address)
      )
      serverSocket.close()
      true
    } catch {
      case _: Exception => false
    }

}
