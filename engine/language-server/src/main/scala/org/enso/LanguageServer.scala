package org.enso

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.stream.ActorMaterializer
import org.graalvm.polyglot.Context
import scala.io.StdIn

object LanguageServer {
  final case class Config(protocol: String, host: String, port: Int) {
    def addressString: String = s"http://$host:$port"
  }
}

/**
  * The language server component wraps the runtime itself,
  * and uses the APIs provided by the interpreter and the compiler to service the requests sent to the Enso Engine.
  */
class LanguageServer(context: Context) {
  implicit val system: ActorSystem                = ActorSystem()
  implicit val materializer: ActorMaterializer    = ActorMaterializer()
  import system.dispatcher

  def run(/*config: LanguageServer.Config*/): Unit = {
//    val route =
//      path("test") {
//        get {
//          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "I'm running"))
//        }
//      }
//
//    val bindingFuture = Http().bindAndHandle(route, config.host, config.port)
//
//    println(s"Server online at ${config.addressString}/test\nPress ENTER to shut down")
//
//    StdIn.readLine()
//
//    println("Shutting down")
//
//    bindingFuture
//      .flatMap(_.unbind())
//      .onComplete(_ => {
//        system.terminate()
//      })

  }
}
