package org.enso.gateway.protocol.request

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.enso.gateway.protocol.request.Param._
import cats.syntax.functor._
import org.enso.gateway.Protocol.DocumentUri
import io.circe.shapes._
import org.enso.gateway.Protocol.ShapesDerivation._

/**
  * [[org.enso.gateway.protocol.RequestOrNotification]] params
  */
sealed trait Params

object Params {
  implicit val paramsDecoder: Decoder[Params] = List[Decoder[Params]](
    Decoder[InitializeParams].widen,
    Decoder[InitializedParams].widen
  ).reduceLeft(_ or _)

  case class Array(value: Seq[Option[Param]]) extends Params

  /**
    * Params of the request [[org.enso.gateway.protocol.initialize]]
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
    implicit val initializeParamsEncoder: Encoder[InitializeParams] =
      deriveEncoder
    implicit val initializeParamsDecoder: Decoder[InitializeParams] =
      deriveDecoder
  }

  /**
    * Params of the notification [[org.enso.gateway.protocol.initialized]]
    */
  case class InitializedParams() extends Params

  object InitializedParams {
    implicit val initializedParamsEncoder: Encoder[InitializedParams] =
      deriveEncoder
    implicit val initializedParamsDecoder: Decoder[InitializedParams] =
      deriveDecoder
  }

}
