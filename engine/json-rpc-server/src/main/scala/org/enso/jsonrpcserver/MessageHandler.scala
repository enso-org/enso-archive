package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, Stash}
import org.enso.jsonrpcserver.MessageHandler.{
  Connected,
  IncomingMessage,
  OutgoingMessage
}

class MessageHandler(val protocol: Protocol, val handler: ActorRef)
    extends Actor
    with Stash {

  override def receive: Receive = {
    case Connected(outConnection) =>
      unstashAll()
      context.become(established(outConnection))
    case _ => stash()
  }

  def established(outConnection: ActorRef): Receive = {
    case IncomingMessage(msg) =>
      val bareMsg = Bare.parse(msg)
      bareMsg match {
        case Some(Bare.Request(methodName, id, params)) =>
          val request: Either[Error, Request[_]] = for {
            method <- protocol.resolveMethod(methodName).toRight(MethodNotFound)
            serializer <- protocol
              .getRequestSerializer(method)
              .toRight(InvalidRequest)
            parsedParams <- serializer
              .decodeRequest(params)
              .toRight(InvalidParams)
          } yield Request(method, id, parsedParams)
          request.foreach(handler ! _)
      }
      println(Bare.parse(msg))

      outConnection ! OutgoingMessage(msg)
  }
}

object MessageHandler {
  case class IncomingMessage(message: String)
  case class OutgoingMessage(message: String)
  case class Connected(outConnection: ActorRef)
}
