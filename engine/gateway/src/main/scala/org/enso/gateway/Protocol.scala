package org.enso.gateway

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import io.circe
import io.circe.CursorOp.DownField
import io.circe.{DecodingFailure, ParsingFailure, Printer}
import io.circe.parser._
import io.circe.syntax._
import org.enso.gateway.protocol._
import org.enso.gateway.protocol.request.IdHolder
import org.enso.gateway.protocol.response.ResponseError.{
  InitializeError,
  MethodNotFoundError,
  ParseError,
  UnexpectedError
}
import org.enso.gateway.protocol.response.error.{Data, ErrorMessage}

import scala.concurrent.Future

object Protocol {

  /**
    * A string specifying the version of the JSON-RPC protocol. MUST be exactly "2.0".
    *
    * @see LSP Spec: https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#contentPart
    * @see JSON-RPC Spec: https://www.jsonrpc.org/specification#request_object
    */
  val jsonRpcVersion = "2.0"
}

/**
  * Helper for implementing protocol over text-based transport.
  * Requests and responses are marshaled as text using JSON-RPC.
  */
class Protocol(gateway: ActorRef)(implicit system: ActorSystem) {

  import system.dispatcher

  /**
    *
    */
  def getTextOutput(
    input: String
  )(implicit timeout: Timeout): Future[Option[String]] = {
    // necessary for Left cases
    val id = decode[IdHolder](input).map(_.id).toOption

    decode[RequestOrNotification](input) match {
      case Right(notification: Notification[_]) =>
        gateway ! notification
        Future.successful(None)

      case Right(request: Request[_]) =>
        val responseFuture =
          (gateway ? request).mapTo[Response]
        responseFuture.map(
          response => encodeToJson(response)
        )

      case Left(err: ParsingFailure) =>
        Future.successful(
          encodeToJson(mkParseErrorResponse(id, input, err))
        )

      case Left(
          DecodingFailure(_, List(DownField(Notification.jsonrpcField)))
          ) =>
        Future.successful(
          encodeToJson(mkInitializeErrorResponse(id))
        )

      case Left(
          DecodingFailure(_, List(DownField(Notification.methodField)))
          ) =>
        Future.successful(
          encodeToJson(mkMethodNotFoundResponse(id))
        )

      case Left(err) =>
        Future.successful(
          encodeToJson(mkUnexpectedErrorResponse(id, err))
        )
    }
  }

  private def mkUnexpectedErrorResponse(id: Option[Id], err: circe.Error) = {
    Response.error(
      id = id,
      error = UnexpectedError(
        data = Some(
          Data.Text(err.toString)
        )
      )
    )
  }

  private def mkMethodNotFoundResponse(id: Option[Id]) = {
    Response.error(
      id    = id,
      error = MethodNotFoundError()
    )
  }

  private def mkInitializeErrorResponse(id: Option[Id]) = {
    Response.error(
      id = id,
      error = InitializeError(
        data = Some(
          Data
            .InitializeData(
              retry = false
            )
        )
      )
    )
  }

  private def mkParseErrorResponse(
    id: Option[Id],
    input: String,
    err: ParsingFailure
  ) = {
    Response.error(
      id = id,
      error = ParseError(
        ErrorMessage.invalidJson,
        Some(
          Data
            .ParseData(
              input,
              err.toString
            )
        )
      )
    )
  }

  private def encodeToJson(
    response: Response
  ): Option[String] = {
    Some(
      response.asJson.printWith(
        Printer.noSpaces.copy(dropNullValues = true)
      )
    )
  }
}
