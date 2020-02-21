package org.enso.languageserver.filemanager

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, InvalidPathException, Paths}

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import org.enso.languageserver.filemanager.failures.{
  InvalidPath,
  IoFailure,
  WriteFailure
}
import shapeless.Coproduct

class FileSystemInterpreter[F[_]: Sync] extends FileSystem[F] {

  override def write(
    path: String,
    content: String
  ): EitherT[F, WriteFailure, Unit] = {
    val maybePath =
      Either
        .catchOnly[InvalidPathException](Paths.get(path))
        .leftMap(ex => Coproduct[WriteFailure](InvalidPath(ex.getReason)))
        .toEitherT[F]

    for {
      parsedPath <- maybePath
      _ <- EitherT {
        Sync[F].delay {
          Either
            .catchOnly[IOException](
              Files.write(parsedPath, content.getBytes(StandardCharsets.UTF_8))
            )
            .leftMap(ex => Coproduct[WriteFailure](IoFailure(ex.getMessage)))
        }
      }
    } yield ()

  }
}
