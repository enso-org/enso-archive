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

/**
  * ZIO implementation of [[FileStorage]]. It uses circe [[Encoder]] and
  * [[Decoder]] to encode/decode object.
  *
  * @param path a path to a file that stores serialized object
  * @param fileSystem a filesystem algebra
  * @param semaphore a semaphore to synchronize access to the file
  * @tparam A a datatype to store
  */
class ZioFileStorage[A: Encoder: Decoder: Default](
  path: File,
  fileSystem: FileSystem[ZIO[ZEnv, +*, +*]],
  semaphore: Semaphore
) extends FileStorage[A, ZIO[ZEnv, +*, +*]] {

  /**
    * Loads the serialized object from the file.
    *
    * @return either [[LoadFailure]] or the object
    */
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
          case Inr(Inl(FileNotFound)) => ZIO.succeed(Default[A].value)
          case other                  => ZIO.fail(other)
        },
        success = ZIO.succeed(_)
      )

  /**
    * Persists the provided object on the disk.
    *
    * @param data a data object
    * @return either [[FileSystemFailure]] or success
    */
  override def persist(data: A): ZIO[ZEnv, FileSystemFailure, Unit] =
    fileSystem.overwriteFile(path, data.asJson.spaces2)

  /**
    * Atomically modifies persisted object using function `f`.
    *
    * @param f the update function that takes the current version of the object
    *          loaded from the disk and returns a tuple containing the new
    *          version of object 3and value to return
    * @tparam B a type of returned value
    * @return either [[LoadFailure]] or the result of updating object
    */
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
