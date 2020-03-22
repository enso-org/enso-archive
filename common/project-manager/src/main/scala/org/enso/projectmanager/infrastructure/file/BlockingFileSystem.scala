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

/**
  * ZIO implementation of [[FileSystem]]. This implementation uses blocking
  * API to access data on the disk.
  *
  * @param operationTimeout a timeout for IO operations
  */
class BlockingFileSystem(operationTimeout: FiniteDuration)
    extends FileSystem[ZIO[ZEnv, *, *]] {

  private val ioTimeout = Duration.fromScala(operationTimeout)

  /**
    * Reads the contents of a textual file.
    *
    * @param file path to the file
    * @return either [[FileSystemFailure]] or the content of a file as a String
    */
  override def readFile(file: File): ZIO[ZEnv, FileSystemFailure, String] =
    effectBlocking { FileUtils.readFileToString(file, Encoding) }
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  /**
    * Writes textual content to a file.
    *
    * @param file path to the file
    * @param contents a textual contents of the file
    * @return either [[FileSystemFailure]] or Unit
    */
  override def overwriteFile(
    file: File,
    contents: String
  ): ZIO[ZEnv, FileSystemFailure, Unit] =
    effectBlocking { FileUtils.write(file, contents, Encoding) }
      .mapError(toFsFailure)
      .timeoutFail(OperationTimeout)(ioTimeout)

  /**
    * Deletes the specified directory recursively.
    *
    * @param path a path to the directory
    * @return either [[FileSystemFailure]] or Unit
    */
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
