package org.enso

import akka.actor.{Actor, Props}
import org.graalvm.polyglot.Context

/**
  * The language server component wraps the runtime itself,
  * and uses the APIs provided by the interpreter and the compiler to service the requests sent to the Enso Engine.
  */
class LanguageServer(context: Context) extends Actor {
  override def receive: Receive = {
    case LanguageServer.Initialize() =>
      sender() ! "Initialize received"
    case LanguageServer.Initialized() =>
      sender() ! "Initialized received"
  }
}

object LanguageServer {
  case class Initialize()
  case class Initialized()

  def props(context: Context): Props = Props(new LanguageServer(context))
}
