package org.enso.projectmanager.infrastructure.file

import java.io.File

import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.enso.projectmanager.data.Default
import org.enso.projectmanager.infrastructure.file.FileStorage._
import org.enso.projectmanager.infrastructure.file.FileSystemFailure.FileNotFound
import shapeless.{:+:, CNil, _}
import zio._

class ZioFileStorage[A: Encoder: Decoder: Default](
  path: File,
  fileSystem: FileSystem[ZIO[ZEnv, *, *]],
  semaphore: Semaphore
) extends FileStorage[A, ZIO[ZEnv, *, *]] {

  override def load(): ZIO[ZEnv, LoadFailure, A] =
    fileSystem
      .readFile(path)
      .mapError(Coproduct[LoadFailure](_))
      .flatMap { contents =>
        decode[A](contents).fold(
          failure =>
            ZIO.fail(
              Coproduct[LoadFailure](CannotDecodeData(failure.getMessage))
            ),
          ZIO.succeed(_)
        )
      }
      .foldM(
        failure = {
          case Inr(Inl(FileNotFound)) => ZIO.succeed(Default[A].default)
          case other                  => ZIO.fail(other)
        },
        success = ZIO.succeed(_)
      )

  override def persist(data: A): ZIO[ZEnv, FileSystemFailure, Unit] =
    fileSystem.overwriteFile(path, data.asJson.spaces2)

  override def modify[B](
    f: A => (A, B)
  ): ZIO[ZEnv, CannotDecodeData :+: FileSystemFailure :+: CNil, B] =
    // format: off
    semaphore.withPermit {
      for {
        index             <- load()
        (updated, output)  = f(index)
        _                 <- persist(updated).mapError(Coproduct[LoadFailure](_))
      } yield output
    }
    // format: on

}
