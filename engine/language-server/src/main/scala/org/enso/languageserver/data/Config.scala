package org.enso.languageserver.data

import java.io.File
import java.util.UUID

import org.enso.languageserver.filemanager.{
  ContentRootNotFound,
  FileSystemFailure
}

import scala.concurrent.duration.FiniteDuration

case class Timeouts(request: FiniteDuration, io: FiniteDuration)

/**
  * The config of the running Language Server instance.
  *
  * @param contentRoots a mapping between content root id and absolute path to
  *                     the content root
  */
case class Config(contentRoots: Map[UUID, File], timeouts: Timeouts) {

  def findContentRoot(rootId: UUID): Either[FileSystemFailure, File] =
    contentRoots
      .get(rootId)
      .toRight(ContentRootNotFound)

}
