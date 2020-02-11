package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, Stash}
import io.circe.Json
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
        case None =>
          outConnection ! OutgoingMessage(makeError(None, ParseError))
        case Some(Bare.Request(methodName, id, params)) =>
          val request: Either[Error, Request[Method]] = for {
            method <- protocol.resolveMethod(methodName).toRight(MethodNotFound)
            decoder <- protocol
              .getParamsDecoder(method)
              .toRight(InvalidRequest)
            parsedParams <- decoder
              .decodeJson(params)
              .left
              .map(_ => InvalidParams)
          } yield Request(method, id, parsedParams)
          request match {
            case Left(error) =>
              outConnection ! OutgoingMessage(makeError(Some(id), error))
            case Right(result) => handler ! result
          }
          request.foreach(handler ! _)
        case Some(Bare.Notification(methodName, params)) =>
          val notification: Either[Error, Notification[Method]] = for {
            method  <- protocol.resolveMethod(methodName).toRight(MethodNotFound)
            decoder <- protocol.getParamsDecoder(method).toRight(InvalidRequest)
            parsedParams <- decoder
              .decodeJson(params)
              .left
              .map(_ => InvalidParams)
          } yield Notification(method, parsedParams)
          notification.foreach(handler ! _)
      }
    case resp: ResponseResult[Method] =>
      val responseDataJson: Json = protocol.allStuffEncoder(resp.data)
      val bareResp               = Bare.ResponseResult(resp.id, responseDataJson)
      outConnection ! OutgoingMessage(Bare.encode(bareResp))

    case notif: Notification[Method] =>
      val paramsJson       = protocol.allStuffEncoder(notif.params)
      val bareNotification = Bare.Notification(notif.tag.name, paramsJson)
      outConnection ! OutgoingMessage(Bare.encode(bareNotification))

  }

  def makeError(id: Option[String], error: Error): String = {
    val bareError         = Bare.ErrorData(error.code, error.message)
    val bareErrorResponse = Bare.ResponseError(id, bareError)
    Bare.encode(bareErrorResponse)
  }
}

object MessageHandler {
  case class IncomingMessage(message: String)
  case class OutgoingMessage(message: String)
  case class Connected(outConnection: ActorRef)
}
