package org.enso.gateway.protocol.request

import io.circe.CursorOp.DownField
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.enso.gateway.protocol.request.Params.DocumentUri

sealed abstract class ChangeFile[FOpts <: FileOptions](
  kind: String,
  options: Option[FOpts]
)

object ChangeFile {
  private val kindField = "kind"

  case class Create(
    uri: DocumentUri,
    options: Option[FileOptions.CreateOrRename] = None
  ) extends ChangeFile[FileOptions.CreateOrRename](Create.kind, options)
  object Create {
    val kind = "create"

    implicit def changeFileCreateDecoder: Decoder[Create] = deriveDecoder

    implicit def changeFileCreateEncoder: Encoder[Create] = deriveEncoder
  }

  case class Rename(
    oldUri: DocumentUri,
    newUri: DocumentUri,
    options: Option[FileOptions.CreateOrRename] = None
  ) extends ChangeFile[FileOptions.CreateOrRename](Rename.kind, options)
  object Rename {
    val kind = "rename"

    implicit def changeFileRenameDecoder: Decoder[Rename] = deriveDecoder

    implicit def changeFileRenameEncoder: Encoder[Rename] = deriveEncoder
  }

  case class Delete(
    uri: DocumentUri,
    options: Option[FileOptions.Delete]
  ) extends ChangeFile[FileOptions.Delete](Delete.kind, options)
  object Delete {
    val kind = "delete"

    implicit def changeFileDeleteDecoder: Decoder[Delete] = deriveDecoder

    implicit def changeFileDeleteEncoder: Encoder[Delete] = deriveEncoder
  }

  implicit def changeFileDecoder[FOpts <: FileOptions]
    : Decoder[ChangeFile[FOpts]] =
    cursor => {
      val kindCursor = cursor.downField(kindField)
      Decoder[String]
        .tryDecode(kindCursor)
        .flatMap(selectDecoder(_).apply(cursor))
    }

  implicit val changeFileCreateRenameEncoder
    : Encoder[ChangeFile[FileOptions.CreateOrRename]] = Encoder.instance {
    case change: Create => change.asJson
    case change: Rename => change.asJson
  }

  implicit val changeFileDeleteEncoder
    : Encoder[ChangeFile[FileOptions.Delete]] = Encoder.instance {
    case change: Delete => change.asJson
  }

  private def selectDecoder[FOpts <: FileOptions](
    kind: String
  ): Decoder[ChangeFile[FOpts]] =
    (kind match {
      case Create.kind => Decoder[Create]
      case Rename.kind => Decoder[Rename]
      case Delete.kind => Decoder[Delete]
      case k =>
        Decoder.failed(
          unknownKindFailure(k)
        )
    }).asInstanceOf[Decoder[ChangeFile[FOpts]]]

  private def unknownKindFailure(kind: String): DecodingFailure =
    DecodingFailure(
      unknownKindMessage(kind),
      List(DownField(kindField))
    )

  private def unknownKindMessage(kind: String) = s"Unknown kind $kind"
}
