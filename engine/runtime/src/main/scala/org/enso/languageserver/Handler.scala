package org.enso.languageserver

import java.nio.ByteBuffer

import org.enso.polyglot.{
  ServerApi,
  ServerApiSerialization
}
import org.graalvm.polyglot.io.MessageEndpoint

class Endpoint(handler: Handler) extends MessageEndpoint {
  var client: MessageEndpoint = _

  def setClient(ep: MessageEndpoint): Unit = client = ep

  def sendToClient(msg: ServerApi): Unit =
    client.sendBinary(ServerApiSerialization.serialize(msg))

  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit =
    ServerApiSerialization.deserialize(data).foreach(handler.onMessage)

  override def sendPing(data: ByteBuffer): Unit = client.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}
}

class Handler {
  val endpoint = new Endpoint(this)

  def onMessage(msg: ServerApi): Unit = msg match {
    case ServerApi.CreateContext(id)  => println(s"create context $id")
    case ServerApi.DestroyContext(id) => println(s"destroy context $id")
  }
}
