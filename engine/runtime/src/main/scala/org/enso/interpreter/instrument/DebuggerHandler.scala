package org.enso.interpreter.instrument

import java.nio.ByteBuffer

import org.enso.polyglot.debugger.{Debugger, Request}
import org.graalvm.polyglot.io.MessageEndpoint

import scala.annotation.unused

class DebuggerEndpoint(handler: DebuggerHandler) extends MessageEndpoint {
  var client: MessageEndpoint = _

  def setClient(ep: MessageEndpoint): Unit = client = ep

  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit = {
    Debugger.deserializeRequest(data).foreach(handler.onMessage)
  }

  override def sendPing(data: ByteBuffer): Unit = client.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}
}

class DebuggerHandler {
  val endpoint = new DebuggerEndpoint(this)

  def sendToClient(data: ByteBuffer): Unit = {
    if (endpoint.client == null)
      throw new RuntimeException(
        "Client not initialized"
      ) // TODO probably remove
    endpoint.client.sendBinary(data)
  }

  def hasClient: Boolean = endpoint.client != null

  def onMessage(@unused request: Request): Unit = {}
}
