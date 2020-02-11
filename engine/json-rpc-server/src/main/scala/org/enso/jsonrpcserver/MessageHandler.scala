package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, Stash}
import io.circe.Json
import org.enso.jsonrpcserver.MessageHandler.{
  Connected,
  IncomingMessage,
  OutgoingMessage
}

class MessageHandler[P](val protocol: Protocol[P], val handler: ActorRef)
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
          val request: Either[Error, Request[P]] = for {
            method <- protocol.resolveMethod(methodName).toRight(MethodNotFound)
            decoder <- protocol
              .getRequestSerializer(method)
              .toRight(InvalidRequest)
            parsedParams <- decoder
              .decodeJson(params)
              .left
              .map(_ => InvalidParams)
          } yield Request(method, id, parsedParams)
          request.foreach(handler ! _)
      }
    case resp: ResponseResult[P] =>
      val responseDataJson: Json = protocol.allStuffEncoder(resp.data)
      val bareResp               = Bare.ResponseResult(resp.id, responseDataJson)
      outConnection ! OutgoingMessage(Bare.encode(bareResp))

  }
}

object MessageHandler {
  case class IncomingMessage(message: String)
  case class OutgoingMessage(message: String)
  case class Connected(outConnection: ActorRef)
}
