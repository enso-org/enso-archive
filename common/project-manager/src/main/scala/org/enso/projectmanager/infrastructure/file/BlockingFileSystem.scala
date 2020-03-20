package org.enso.projectmanager.infrastructure.file
import java.io.{File, FileNotFoundException}
import java.nio.file.{AccessDeniedException, NoSuchFileException}

import org.apache.commons.io.{FileExistsException, FileUtils}
import org.enso.projectmanager.infrastructure.file.BlockingFileSystem.Encoding
import org.enso.projectmanager.infrastructure.file.FileSystemFailure._
import zio.blocking._
import zio.duration.Duration
import zio.{ZEnv, ZIO}

import scala.concurrent.duration.FiniteDuration

class BlockingFileSystem(operationTimeout: FiniteDuration)
    extends FileSystem[ZIO[ZEnv, *, *]] {

  private val ioTimeout = Duration.fromScala(operationTimeout)

  override def readFile(file: File): ZIO[ZEnv, FileSystemFailure, String] =
    effectBlocking { FileUtils.readFileToString(file, Encoding) }
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  override def overwriteFile(
    file: File,
    contents: String
  ): ZIO[ZEnv, FileSystemFailure, Unit] =
    effectBlocking { FileUtils.write(file, contents, Encoding) }
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  override def removeDir(path: File): ZIO[ZEnv, FileSystemFailure, Unit] =
    effectBlocking { FileUtils.deleteDirectory(path) }
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

object BlockingFileSystem {

  val Encoding = "UTF-8"

}
