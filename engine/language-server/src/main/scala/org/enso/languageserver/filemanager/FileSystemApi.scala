package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.{Path, Paths}

import scala.collection.immutable.TreeSet

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

  /**
    * An object representing abstract file system entry.
    */
  sealed trait Entry

  object Entry {

    /**
      * An ordering for an Entry.
      *
      * @note scala/bug#10741. [[Ordering]] instance is used not only for
      * ordering the elements, but also for an equality testing. If we just
      * return the path, we would not be able to distinguish between the nodes
      * with the same path. To address this we append the node name to the path.
      */
    implicit val ordering: Ordering[Entry] =
      Ordering.by {
        case DirectoryEntry(path, _) =>
          Paths.get(path.toString, "DirectoryEntry")
        case DirectoryEntryTruncated(path) =>
          Paths.get(path.toString, "DirectoryEntryTruncated")
        case SymbolicLinkLoop(path) =>
          Paths.get(path.toString, "SymbolicLinkLoop")
        case FileEntry(path) =>
          Paths.get(path.toString, "FileEntry")
        case OtherEntry(path) =>
          Paths.get(path.toString, "OtherEntry")
      }
  }

  /**
    * An entry representing a directory.
    *
    * @param path to the directory
    * @children a paths to the children entries
    */
  case class DirectoryEntry(path: Path, children: TreeSet[Entry]) extends Entry

  object DirectoryEntry {

    def empty(path: Path): DirectoryEntry =
      DirectoryEntry(path, TreeSet.empty[Entry])
  }

  /**
    * An entry representing a directory with contents truncated.
    *
    * @param path to the directory
    */
  case class DirectoryEntryTruncated(path: Path) extends Entry

  /**
    * An entry representing the symbolic link that creates an infinite loop.
    *
    * When a symlink loop is detected, instead of returning the
    * [[DirectoryEntry]] node, this entry is returned to break the loop.
    *
    * @param path to the symlink
    */
  case class SymbolicLinkLoop(path: Path) extends Entry

  /**
    * An entry representing a file.
    *
    * @param path to the file
    */
  case class FileEntry(path: Path) extends Entry

  /**
    * Unrecognized file system entry.
    */
  case class OtherEntry(path: Path) extends Entry

}
