package org.enso

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer
import org.graalvm.polyglot.Context

/**
  * The language server component wraps the runtime itself,
  * and uses the APIs provided by the interpreter and the compiler to service the requests sent to the Enso Engine.
  */
class LanguageServer(context: Context) /*extends Actor*/ {
  implicit val system: ActorSystem                = ActorSystem()
  implicit val materializer: ActorMaterializer    = ActorMaterializer()
  import system.dispatcher

  def run(): Unit = {

  }

//  override def receive: Receive = ???
}
