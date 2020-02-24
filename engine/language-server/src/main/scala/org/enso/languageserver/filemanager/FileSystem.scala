package org.enso.languageserver.filemanager

import java.io.IOException
import java.nio.file._

import cats.effect.Sync
import cats.implicits._
import org.apache.commons.io.FileUtils

/**
  * File manipulation facility.
  *
  * @tparam F represents target monad
  */
class FileSystem[F[_]: Sync] extends FileSystemApi[F] {

  /**
    * Writes textual content to a file.
    *
    * @param pathString path to the file
    * @param content    a textual content of the file
    * @return either FileSystemFailure or Unit
    */
  override def write(
    pathString: String,
    content: String
  ): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      for {
        path <- convertPath(pathString)
        _    <- writeStringToFile(path, content)
      } yield ()
    }

  private def convertPath(path: String): Either[FileSystemFailure, Path] =
    Either
      .catchOnly[InvalidPathException](Paths.get(path))
      .leftMap(
        ex =>
          FileSystemFailure(
            s"Cannot convert path string into path: ${ex.getReason}"
          )
      )

  private def writeStringToFile(
    path: Path,
    content: String
  ): Either[FileSystemFailure, Unit] =
    Either
      .catchOnly[IOException](
        FileUtils.write(path.toFile, content, "UTF-8")
      )
      .leftMap {
        case _: AccessDeniedException => FileSystemFailure("Access denied")
        case ex                       => FileSystemFailure(ex.getMessage)
      }
      .map(_ => ())

}
