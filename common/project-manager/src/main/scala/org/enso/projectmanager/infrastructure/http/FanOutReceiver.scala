package org.enso.projectmanager.infrastructure.http

import akka.actor.{Actor, ActorRef}
import org.enso.projectmanager.infrastructure.http.FanOutReceiver.Listen

class FanOutReceiver extends Actor {

  override def receive: Receive = running()

  private def running(listeners: Set[ActorRef] = Set.empty): Receive = {
    case Listen(listener) => context.become(running(listeners + listener))
    case msg              => listeners.foreach(_ ! msg)
  }

}

object FanOutReceiver {

  case class Listen(listener: ActorRef)

}
