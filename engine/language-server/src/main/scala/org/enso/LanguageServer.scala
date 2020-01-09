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

  /**
    * Akka message sent by Gateway received LSP request `initialize`
    */
  case class Initialize()

  /**
    * Akka message sent by Gateway received LSP notification `initialized`
    */
  case class Initialized()

  /**
    * Language server response to [[Initialize]]
    */
  case class InitializeReceived()

  /**
    * Language server response to [[Initialized]]
    */
  case class InitializedReceived()

  def props(context: Context): Props = Props(new LanguageServer(context))
}
