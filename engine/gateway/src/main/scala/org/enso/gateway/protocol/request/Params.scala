package org.enso.gateway.protocol.request

import io.circe.generic.semiauto._
import io.circe.Decoder
import cats.syntax.functor._
import org.enso.gateway.Protocol.DocumentUri
import org.enso.gateway.protocol.request.Param.{
  ClientCapabilities,
  ClientInfo,
  InitializationOptions,
  Trace,
  WorkspaceFolder
}

/**
  * [[org.enso.gateway.protocol.RequestOrNotification]] params
  */
sealed trait Params

// TODO [Dmytro] Add Params.Array. Currently removed because Circe codec is not found
object Params {
  implicit val paramsDecoder: Decoder[Params] = List[Decoder[Params]](
    Decoder[InitializeParams].widen,
    Decoder[InitializedParams].widen
  ).reduceLeft(_ or _)

  /**
    * Params of the request [[org.enso.gateway.protocol.Requests.Initialize]]
    */
  case class InitializeParams(
    processId: Option[Int]                               = None,
    clientInfo: Option[ClientInfo]                       = None,
    rootPath: Option[String]                             = None, //deprecated: use rootUri, LSP Spec
    rootUri: Option[DocumentUri]                         = None,
    initializationOptions: Option[InitializationOptions] = None,
    capabilities: ClientCapabilities,
    trace: Option[Trace]                           = None,
    workspaceFolders: Option[Seq[WorkspaceFolder]] = None
  ) extends Params

  object InitializeParams {
    implicit val initializeParamsDecoder: Decoder[InitializeParams] =
      deriveDecoder
  }

  /**
    * Params of the notification [[org.enso.gateway.protocol.Notifications.Initialized]]
    */
  case class InitializedParams() extends Params

  object InitializedParams {
    implicit val initializedParamsDecoder: Decoder[InitializedParams] =
      deriveDecoder
  }
}
