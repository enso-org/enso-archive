package org.enso.projectmanager.util

import akka.actor.{Actor, ActorLogging}

trait UnhandledLogging extends ActorLogging { this: Actor =>

  override def unhandled(message: Any): Unit =
    log.warning("Received unknown message: {}", message)

}
