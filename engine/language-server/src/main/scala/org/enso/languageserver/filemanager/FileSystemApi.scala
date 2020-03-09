package org.enso.languageserver.filemanager

import java.io.File

/**
  * File manipulation API.
  *
  * @tparam F represents target monad
  */
trait FileSystemApi[F[_]] {

  import FileSystemApi._

  /**
    * Writes textual content to a file.
    *
    * @param file path to the file
    * @param content a textual content of the file
    * @return either [[FileSystemFailure]] or Unit
    */
  def write(
    file: File,
    content: String
  ): F[Either[FileSystemFailure, Unit]]

  /**
    * Reads the contents of a textual file.
    *
    * @param file path to the file
    * @return either [[FileSystemFailure]] or the content of a file as a String
    */
  def read(file: File): F[Either[FileSystemFailure, String]]

  /**
    * Deletes the specified file or directory recursively.
    *
    * @param file path to the file or directory
    * @return either [[FileSystemFailure]] or Unit
    */
  def delete(file: File): F[Either[FileSystemFailure, Unit]]

  /**
    * Creates an empty file with parent directory.
    *
    * @param file path to the file
    * @return
    */
  def createFile(file: File): F[Either[FileSystemFailure, Unit]]

  /**
    * Creates a directory, including any necessary but nonexistent parent
    * directories.
    *
    * @param file path to the file
    * @return
    */
  def createDirectory(file: File): F[Either[FileSystemFailure, Unit]]

  /**
    * Copy a file or directory recursively
    *
    * @param from a path to the source
    * @param to a path to the destination
    * @return either [[FileSystemFailure]] or Unit
    */
  def copy(
    from: File,
    to: File
  ): F[Either[FileSystemFailure, Unit]]

  /**
    * Move a file or directory recursively
    *
    * @param from a path to the source
    * @param to a path to the destination
    * @return either [[FileSystemFailure]] or Unit
    */
  def move(
    from: File,
    to: File
  ): F[Either[FileSystemFailure, Unit]]

  /**
    * Checks if the specified file exists.
    *
    * @param file path to the file or directory
    * @return either [[FileSystemFailure]] or file existence flag
    */
  def exists(file: File): F[Either[FileSystemFailure, Boolean]]

  /**
    * Returns contents of a given path.
    *
    * @param path to the file system object
    * @param depth maximum depth of a directory tree
    * @return either [[FileSystemFailure]] or directory structure
    */
  def tree(
    path: File,
    depth: Option[Int]
  ): F[Either[FileSystemFailure, DirectoryEntry]]
}

object FileSystemApi {

  import java.nio.file.Path

  sealed trait Entry

  case class DirectoryEntry(path: Path, children: Set[Entry]) extends Entry

  object DirectoryEntry {

    def empty(path: Path): DirectoryEntry =
      DirectoryEntry(path, Set.empty)
  }

  case class FileEntry(path: Path) extends Entry

  case class SymbolicLinkEntry(path: Path, target: Path) extends Entry

  case class OtherEntry(path: Path) extends Entry

}
