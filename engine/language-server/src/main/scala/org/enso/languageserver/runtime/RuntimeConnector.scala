package org.enso.languageserver.runtime

import akka.actor.{Actor, ActorLogging, Props, Stash}
import org.enso.polyglot.ServerApi.{CreateContext, DestroyContext}
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
    case DestroyContext(uuid) => log.info(s"Context destroyed: $uuid")
    case CreateContext(uuid) => log.info(s"Context created: $uuid")

  }
}

object RuntimeConnector {

  case class Initialize(engineConnection: MessageEndpoint)

  def props: Props =
    Props(new RuntimeConnector)
}
