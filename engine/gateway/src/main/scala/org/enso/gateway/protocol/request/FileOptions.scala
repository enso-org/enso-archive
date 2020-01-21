package org.enso.gateway.protocol.request

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
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
  }

  case class Delete(
    recursive: Option[Boolean]      = None,
    ignoreIfExists: Option[Boolean] = None
  ) extends FileOptions

  object Delete {
    implicit val fileOptionsDeleteDecoder: Decoder[Delete] =
      deriveDecoder
  }

  implicit val fileOptionsDecoder: Decoder[FileOptions] =
    List[Decoder[FileOptions]](
      Decoder[CreateOrRename].widen,
      Decoder[Delete].widen
    ).reduceLeft(_ or _)
}
