package org.enso.languageserver.filemanager

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{
  Files,
  InvalidPathException,
  Path,
  Paths,
  StandardOpenOption
}

import cats.effect.Sync
import cats.implicits._
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
        Files.write(
          path,
          content.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE
        )
      )
      .leftMap(ex => IoFailure(ex.toString))
      .map(_ => ())

}
