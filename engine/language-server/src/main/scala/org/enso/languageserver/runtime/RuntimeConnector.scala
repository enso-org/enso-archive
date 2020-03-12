package org.enso.languageserver.runtime

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props, Stash}
import org.enso.languageserver.runtime.RuntimeConnector.Destroy
import org.enso.polyglot.LanguageApi
import org.graalvm.polyglot.io.MessageEndpoint

class RuntimeConnector extends Actor with ActorLogging with Stash {

  override def receive: Receive = {
    case RuntimeConnector.Initialize(engine) =>
      log.info("Engine connection established.")
      unstashAll()
      context.become(initialized(engine))
    case _ => stash()
  }

  def initialized(engineConnection: MessageEndpoint): Receive = {
    case Destroy => context.stop(self)
    case LanguageApi.CreateContextResponse(uid) => log.info("Context created {}.", uid)
  }
}

object RuntimeConnector {

  case class Initialize(engineConnection: MessageEndpoint)
  case object Destroy

  def props: Props =
    Props(new RuntimeConnector)
}
