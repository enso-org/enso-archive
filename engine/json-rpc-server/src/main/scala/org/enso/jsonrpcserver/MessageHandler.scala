package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, Stash}
import io.circe.Json
import org.enso.jsonrpcserver.MessageHandler.{
  Connected,
  IncomingMessage,
  OutgoingMessage
}

class MessageHandler(val protocol: Protocol, val controller: ActorRef)
    extends Actor
    with Stash {

  type Id = String

  override def receive: Receive = {
    case Connected(outConnection) =>
      unstashAll()
      context.become(established(outConnection, Map()))
    case _ => stash()
  }

  def established(
    outConnection: ActorRef,
    awaitingResponses: Map[Id, Method]
  ): Receive = {
    case IncomingMessage(msg) =>
      val bareMsg = Bare.parse(msg)
      bareMsg match {
        case None =>
          outConnection ! OutgoingMessage(makeError(None, ParseError))
        case Some(Bare.Request(methodName, id, params)) =>
          val methodAndParams = resolveMethodAndParams(methodName, params)
          methodAndParams match {
            case Left(error) =>
              outConnection ! OutgoingMessage(makeError(Some(id), error))
            case Right((method, params)) =>
              controller ! Request(method, id, params)
          }
        case Some(Bare.Notification(methodName, params)) =>
          val notification = resolveMethodAndParams(methodName, params).map {
            case (method, params) => Notification(method, params)
          }
          notification.foreach(controller ! _)

        case Some(Bare.ResponseResult(mayId, result)) =>
          val maybeDecoded: Option[ResultOf[Method]] = for {
            id           <- mayId
            method       <- awaitingResponses.get(id)
            decoder      <- protocol.getResultDecoder(method)
            parsedResult <- decoder.decodeJson(result).toOption
          } yield parsedResult
          val trueResult = maybeDecoded.getOrElse(UnknownResult(result))
          controller ! ResponseResult(mayId, trueResult)
          mayId.map(
            id =>
              context.become(established(outConnection, awaitingResponses - id))
          )

        case Some(Bare.ResponseError(mayId, bareError)) =>
          val error = protocol
            .resolveError(bareError.code)
            .getOrElse(UnknownError(bareError.code, bareError.message))
          controller ! ResponseError(mayId, error)
          mayId.map(
            id =>
              context.become(established(outConnection, awaitingResponses - id))
          )

      }

    case req: Request[Method] =>
      val paramsJson = protocol.allStuffEncoder(req.params)
      val bareReq    = Bare.Request(req.tag.name, req.id, paramsJson)
      outConnection ! OutgoingMessage(Bare.encode(bareReq))
      context.become(
        established(outConnection, awaitingResponses + (req.id -> req.tag))
      )

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

  def resolveMethodAndParams(
    methodName: String,
    params: Json
  ): Either[Error, (Method, ParamsOf[Method])] =
    for {
      method <- protocol.resolveMethod(methodName).toRight(MethodNotFound)
      decoder <- protocol
        .getParamsDecoder(method)
        .toRight(InvalidRequest)
      parsedParams <- decoder
        .decodeJson(params)
        .left
        .map(_ => InvalidParams)
    } yield (method, parsedParams)
}

object MessageHandler {
  case class IncomingMessage(message: String)
  case class OutgoingMessage(message: String)
  case class Connected(outConnection: ActorRef)
}
