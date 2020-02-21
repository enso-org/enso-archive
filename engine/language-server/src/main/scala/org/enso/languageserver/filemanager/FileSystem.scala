package org.enso.languageserver.filemanager

import java.io.{FileNotFoundException, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{
  Files,
  InvalidPathException,
  NoSuchFileException,
  Path,
  Paths,
  StandardOpenOption
}

import cats.effect.Sync
import cats.implicits._
import org.apache.commons.io.FileUtils
import org.enso.languageserver.filemanager.failures.{
  InvalidPath,
  IoFailure,
  WriteFailure
}
import shapeless.Coproduct

class FileSystem[F[_]: Sync] extends FileSystemApi[F] {

  override def write(
    path: String,
    content: String
  ): F[Either[WriteFailure, Unit]] =
    Sync[F].delay {
      for {
        parsedPath <- parsePath(path).leftMap(Coproduct[WriteFailure](_))
        _          <- write(parsedPath, content).leftMap(Coproduct[WriteFailure](_))
      } yield ()
    }

  private def parsePath(path: String): Either[InvalidPath, Path] =
    Either
      .catchOnly[InvalidPathException](Paths.get(path))
      .leftMap(ex => InvalidPath(ex.getReason))

  private def write(path: Path, content: String): Either[IoFailure, Unit] =
    Either
      .catchOnly[IOException](
        FileUtils.write(path.toFile, content, "UTF-8")
      )
      .leftMap {
        case ex: NoSuchFileException =>
          IoFailure(s"File not found: ${ex.getMessage}")

        case ex: FileNotFoundException =>
          IoFailure(s"File not found: ${ex.getMessage}")

        case ex => IoFailure(ex.getMessage)
      }
      .map(_ => ())

}
