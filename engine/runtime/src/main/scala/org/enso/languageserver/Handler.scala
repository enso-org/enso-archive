package org.enso.languageserver

import java.nio.ByteBuffer

import org.enso.polyglot.{
  LanguageApi,
  ServerApiSerialization
}
import org.graalvm.polyglot.io.MessageEndpoint

class Endpoint(handler: Handler) extends MessageEndpoint {
  var client: MessageEndpoint = _

  def setClient(ep: MessageEndpoint): Unit = client = ep

  def sendToClient(msg: LanguageApi): Unit =
    client.sendBinary(LanguageApi.serialize(msg))

  override def sendText(text: String): Unit = {}

  override def sendBinary(data: ByteBuffer): Unit =
    LanguageApi.deserialize(data).foreach(handler.onMessage)

  override def sendPing(data: ByteBuffer): Unit = client.sendPong(data)

  override def sendPong(data: ByteBuffer): Unit = {}

  override def sendClose(): Unit = {}
}

class Handler {
  val endpoint = new Endpoint(this)

  def onMessage(msg: LanguageApi): Unit = msg match {
    case LanguageApi.CreateContext(id)  => println(s"create context $id")
    case LanguageApi.DestroyContext(id) => println(s"destroy context $id")
  }
}
