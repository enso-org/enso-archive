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
      sender() ! LanguageServer.InitializeReceived()
    case LanguageServer.Initialized() =>
      sender() ! LanguageServer.InitializedReceived()
  }
}

object LanguageServer {
  case class Initialize()
  case class Initialized()

  case class InitializeReceived()
  case class InitializedReceived()

  def props(context: Context): Props = Props(new LanguageServer(context))
}
