package org.enso.gateway.protocol.request

import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import cats.syntax.functor._

sealed trait FileOptions {
  def ignoreIfExists: Option[Boolean]
}
object FileOptions {

  case class CreateOrRename(
    overwrite: Option[Boolean]      = None,
    ignoreIfExists: Option[Boolean] = None
  ) extends FileOptions
  object CreateOrRename {
    implicit val fileOptionsCreateOrRenameDecoder: Decoder[CreateOrRename] =
      deriveDecoder
    implicit val fileOptionsCreateOrRenameEncoder: Encoder[CreateOrRename] =
      deriveEncoder
  }

  case class Delete(
    recursive: Option[Boolean]      = None,
    ignoreIfExists: Option[Boolean] = None
  ) extends FileOptions
  object Delete {
    implicit val fileOptionsDeleteDecoder: Decoder[Delete] =
      deriveDecoder
    implicit val fileOptionsDeleteEncoder: Encoder[Delete] =
      deriveEncoder
  }

  implicit val fileOptionsDecoder: Decoder[FileOptions] =
    List[Decoder[FileOptions]](
      Decoder[CreateOrRename].widen,
      Decoder[Delete].widen
    ).reduceLeft(_ or _)

  implicit val fileOptionsEncoder: Encoder[FileOptions] =
    Encoder.instance {
      case option: CreateOrRename => option.asJson
      case option: Delete         => option.asJson
    }
}
