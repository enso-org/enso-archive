package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Timers}
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout

import scala.io.StdIn

object Main {

  case class Connect(client: ActorRef)
  case object Ping
  case object Request
  case object Response
  case class DoRespond(sender: ActorRef)

  class Server extends Actor with Timers {
    override def receive: Receive = {

      case Connect(client) =>
        val uuid = java.util.UUID.randomUUID
        timers.startPeriodicTimer(uuid, Ping, 100.milliseconds)
        context.become(connected(client))
    }

    def connected(client: ActorRef): Receive = {
      case Ping => client ! Ping
      case Request =>
        timers.startSingleTimer(new Object, DoRespond(sender), 1.second)
      case DoRespond(s) => s ! Response
    }
  }

  class Client(val server: ActorRef) extends Actor {
    implicit val timeout: Timeout = Timeout(5.seconds) // needed for `?` below
    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global

    override def receive: Receive = {
      case Ping => println("Received ping.")
      case Request =>
        println("Sending request.")
        ask(server, Request).map(r => println(s"Received response: $r"))
    }
  }

  def main(args: Array[String]): Unit = {

    implicit val system       = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val server = system.actorOf(Props(new Server))
    val client = system.actorOf(Props(new Client(server)))

    server ! Connect(client)

    client ! Request

    StdIn.readLine()
    system.terminate()

  }
}
