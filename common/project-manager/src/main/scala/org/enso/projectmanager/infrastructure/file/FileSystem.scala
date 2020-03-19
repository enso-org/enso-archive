package org.enso.projectmanager.infrastructure.file
import java.io.{File, FileNotFoundException}
import java.nio.file.{AccessDeniedException, NoSuchFileException}

import org.apache.commons.io.{FileExistsException, FileUtils}
import org.enso.projectmanager.infrastructure.file.FileSystem.Encoding
import org.enso.projectmanager.infrastructure.file.FileSystemFailure.{
  AccessDenied,
  FileExists,
  FileNotFound,
  GenericFileSystemFailure,
  OperationTimeout
}
import zio.blocking._
import zio.duration.Duration
import zio.{ZEnv, ZIO}

import scala.concurrent.duration.FiniteDuration

class FileSystem(operationTimeout: FiniteDuration) extends FileSystemApi {

  private val ioTimeout = Duration.fromScala(operationTimeout)

  override def readFile(file: File): ZIO[ZEnv, FileSystemFailure, String] =
    effectBlocking(FileUtils.readFileToString(file, Encoding))
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  override def overwriteFile(
    file: File,
    contents: String
  ): ZIO[ZEnv, FileSystemFailure, Unit] =
    effectBlocking(FileUtils.write(file, contents, Encoding))
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  private val toFsFailure: Throwable => FileSystemFailure = {
    case _: FileNotFoundException => FileNotFound
    case _: NoSuchFileException   => FileNotFound
    case _: FileExistsException   => FileExists
    case _: AccessDeniedException => AccessDenied
    case ex                       => GenericFileSystemFailure(ex.getMessage)
  }

}

object FileSystem {

  val Encoding = "UTF-8"

}
