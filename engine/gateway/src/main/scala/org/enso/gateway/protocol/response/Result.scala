package org.enso.gateway.protocol.response

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto.{
  deriveUnwrappedDecoder,
  deriveUnwrappedEncoder
}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.response.result.{
  ServerCapabilities,
  ServerInfo
}
import io.circe.syntax._
import org.enso.gateway.protocol.TextEdit
import cats.syntax.functor._

/** Result of [[org.enso.gateway.protocol.Response]].
  *
  * LSP Spec:
  * https://microsoft.github.io/language-server-protocol/specifications/specification-3-15/#responseMessage
  */
sealed trait Result
object Result {
  implicit val resultEncoder: Encoder[Result] = Encoder.instance {
    case text: Text               => text.asJson
    case number: Number           => number.asJson
    case boolean: Bool            => boolean.asJson
    case result: InitializeResult => result.asJson
    case result: ApplyWorkspaceEditResult =>
      result.asJson
    case result: WillSaveTextDocumentWaitUntilResult =>
      result.asJson
    case result: NullResult.type => result.asJson
  }

  implicit val resultDecoder: Decoder[Result] = List[Decoder[Result]](
    Decoder[Text].widen,
    Decoder[Number].widen,
    Decoder[Bool].widen,
    Decoder[InitializeResult].widen,
    Decoder[ApplyWorkspaceEditResult].widen,
    Decoder[WillSaveTextDocumentWaitUntilResult].widen,
    Decoder[NullResult.type].widen
  ).reduceLeft(_ or _)

  /** A string result. */
  case class Text(value: String) extends Result
  object Text {
    implicit val resultStringEncoder: Encoder[Text] = deriveUnwrappedEncoder
    implicit val resultStringDecoder: Decoder[Text] = deriveUnwrappedDecoder
  }

  /** A number result. */
  case class Number(value: Int) extends Result
  object Number {
    implicit val resultNumberEncoder: Encoder[Number] = deriveUnwrappedEncoder
    implicit val resultNumberDecoder: Decoder[Number] = deriveUnwrappedDecoder
  }

  /** A boolean result. */
  case class Bool(value: Boolean) extends Result
  object Bool {
    implicit val resultBooleanEncoder: Encoder[Bool] =
      deriveUnwrappedEncoder
    implicit val resultBooleanDecoder: Decoder[Bool] =
      deriveUnwrappedDecoder
  }

  /** Result of [[org.enso.gateway.protocol.Requests.Initialize]]. */
  case class InitializeResult(
    capabilities: ServerCapabilities,
    serverInfo: Option[ServerInfo] = None
  ) extends Result
  object InitializeResult {
    implicit val initializeResultEncoder: Encoder[InitializeResult] =
      deriveEncoder
    implicit val initializeResultDecoder: Decoder[InitializeResult] =
      deriveDecoder
  }

  /** Result of [[org.enso.gateway.protocol.Requests.Shutdown]]. */
  case object NullResult extends Result {
    implicit val nullResultEncoder: Encoder[NullResult.type] = _ => Json.Null
    implicit val nullResultDecoder: Decoder[NullResult.type] = _ =>
      Right(NullResult)
  }

  case class ApplyWorkspaceEditResult(
    applied: Boolean,
    failureReason: Option[String] = None
  ) extends Result
  object ApplyWorkspaceEditResult {
    implicit val applyWorkspaceEditResultEncoder
      : Encoder[ApplyWorkspaceEditResult] =
      deriveEncoder
    implicit val applyWorkspaceEditResultDecoder
      : Decoder[ApplyWorkspaceEditResult] =
      deriveDecoder
  }

  case class WillSaveTextDocumentWaitUntilResult(
    value: Option[Seq[TextEdit]] = None
  ) extends Result
  object WillSaveTextDocumentWaitUntilResult {
    implicit val willSaveTextDocumentWaitUntilResultEncoder
      : Encoder[WillSaveTextDocumentWaitUntilResult] =
      deriveUnwrappedEncoder
    implicit val willSaveTextDocumentWaitUntilResultDecoder
      : Decoder[WillSaveTextDocumentWaitUntilResult] =
      deriveUnwrappedDecoder
  }
}
