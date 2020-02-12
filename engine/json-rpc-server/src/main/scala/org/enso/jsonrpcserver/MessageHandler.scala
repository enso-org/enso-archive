package org.enso.jsonrpcserver
import akka.actor.{Actor, ActorRef, Stash}
import io.circe.Json
import org.enso.jsonrpcserver.MessageHandler.{Connected, WebMessage}

/**
  * An actor responsible for passing parsed massages between the web and
  * a controller actor.
  * @param protocol a protocol object describing supported messages and their
  *                 serialization modes.
  * @param controller the controller actor, handling parsed messages.
  */
class MessageHandler(val protocol: Protocol, val controller: ActorRef)
    extends Actor
    with Stash {

  /**
    * A pre-initialization behavior, awaiting a to-web connection end.
    * @return the actor behavior.
    */
  override def receive: Receive = {
    case Connected(webConnection) =>
      unstashAll()
      context.become(established(webConnection, Map()))
    case _ => stash()
  }

  /**
    * A fully established connection behavior.
    * @param webConnection the to-web connection end.
    * @param awaitingResponses a list of all requests sent to web, retained for
    *                          response deserialization.
    * @return the connected actor behavior.
    */
  def established(
    webConnection: ActorRef,
    awaitingResponses: Map[Id, Method]
  ): Receive = {
    case WebMessage(msg) =>
      val bareMsg = JsonProtocol.parse(msg)
      bareMsg match {
        case None =>
          webConnection ! WebMessage(makeError(None, Errors.ParseError))
        case Some(JsonProtocol.Request(methodName, id, params)) =>
          val methodAndParams = resolveMethodAndParams(methodName, params)
          methodAndParams match {
            case Left(error) =>
              webConnection ! WebMessage(makeError(Some(id), error))
            case Right((method, params)) =>
              controller ! Request(method, id, params)
          }
        case Some(JsonProtocol.Notification(methodName, params)) =>
          val notification = resolveMethodAndParams(methodName, params).map {
            case (method, params) => Notification(method, params)
          }
          notification.foreach(controller ! _)

        case Some(JsonProtocol.ResponseResult(mayId, result)) =>
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
              context.become(established(webConnection, awaitingResponses - id))
          )

        case Some(JsonProtocol.ResponseError(mayId, bareError)) =>
          val error = protocol
            .resolveError(bareError.code)
            .getOrElse(Errors.UnknownError(bareError.code, bareError.message))
          controller ! ResponseError(mayId, error)
          mayId.map(
            id =>
              context.become(established(webConnection, awaitingResponses - id))
          )

      }

    case req: Request[Method] =>
      val paramsJson = protocol.payloadsEncoder(req.params)
      val bareReq    = JsonProtocol.Request(req.tag.name, req.id, paramsJson)
      webConnection ! WebMessage(JsonProtocol.encode(bareReq))
      context.become(
        established(webConnection, awaitingResponses + (req.id -> req.tag))
      )

    case resp: ResponseResult[Method] =>
      val responseDataJson: Json = protocol.payloadsEncoder(resp.data)
      val bareResp               = JsonProtocol.ResponseResult(resp.id, responseDataJson)
      webConnection ! WebMessage(JsonProtocol.encode(bareResp))

    case resp: ResponseError =>
      val bareError =
        JsonProtocol.ErrorData(resp.error.code, resp.error.message)
      val bareResponse = JsonProtocol.ResponseError(resp.id, bareError)
      webConnection ! WebMessage(JsonProtocol.encode(bareResponse))

    case notif: Notification[Method] =>
      val paramsJson = protocol.payloadsEncoder(notif.params)
      val bareNotification =
        JsonProtocol.Notification(notif.tag.name, paramsJson)
      webConnection ! WebMessage(JsonProtocol.encode(bareNotification))

  }

  private def makeError(id: Option[Id], error: Error): String = {
    val bareError         = JsonProtocol.ErrorData(error.code, error.message)
    val bareErrorResponse = JsonProtocol.ResponseError(id, bareError)
    JsonProtocol.encode(bareErrorResponse)
  }

  private def resolveMethodAndParams(
    methodName: String,
    params: Json
  ): Either[Error, (Method, ParamsOf[Method])] =
    for {
      method <- protocol
        .resolveMethod(methodName)
        .toRight(Errors.MethodNotFound)
      decoder <- protocol
        .getParamsDecoder(method)
        .toRight(Errors.InvalidRequest)
      parsedParams <- decoder
        .decodeJson(params)
        .left
        .map(_ => Errors.InvalidParams)
    } yield (method, parsedParams)
}

/**
  * Control messages for the [[MessageHandler]] actor.
  */
object MessageHandler {

  /**
    * A message exchanged on the Web side of the boundary.
    *
    * @param message the serialized json contents of the message.
    */
  case class WebMessage(message: String)

  /**
    * A control message used for [[MessageHandler]] initializations
    * @param webConnection the actor representing the web.
    */
  case class Connected(webConnection: ActorRef)
}
