package org.enso.languageserver.runtime

import java.nio.ByteBuffer
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import org.enso.languageserver.runtime.RuntimeConnector.Destroy
import org.enso.polyglot.LanguageApi
import org.graalvm.polyglot.io.MessageEndpoint

class RuntimeConnector extends Actor with ActorLogging with Stash {

  override def receive: Receive = {
    case RuntimeConnector.Initialize(engine) =>
      log.info("Engine connection established.")
      engine.sendBinary(
        LanguageApi
          .serialize(LanguageApi.CreateContextRequest(UUID.randomUUID()))
      )
      unstashAll()
      context.become(initialized(engine))
    case _ => stash()
  }

  def initialized(engineConnection: MessageEndpoint): Receive = {
    case Destroy => context.stop(self)
    case LanguageApi.CreateContextResponse(uid) =>
      log.info("Context created {}.", uid)
  }
}

object RuntimeConnector {

  case class Initialize(engineConnection: MessageEndpoint)
  case object Destroy

  def props: Props =
    Props(new RuntimeConnector)

  class Endpoint(actor: ActorRef, peerEndpoint: MessageEndpoint)
      extends MessageEndpoint {
    override def sendText(text: String): Unit = {}

    override def sendBinary(data: ByteBuffer): Unit =
      LanguageApi
        .deserialize(data)
        .foreach(actor ! _)

    override def sendPing(data: ByteBuffer): Unit = peerEndpoint.sendPong(data)

    override def sendPong(data: ByteBuffer): Unit = {}

    override def sendClose(): Unit = actor ! RuntimeConnector.Destroy
  }
}
