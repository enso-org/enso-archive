package org.enso.languageserver.filemanager

import java.io.{File, FileNotFoundException, IOException}
import java.nio.file._

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import org.apache.commons.io.{FileExistsException, FileUtils}

/**
  * File manipulation facility.
  *
  * @tparam F represents target monad
  */
class FileSystem[F[_]: Sync] extends FileSystemApi[F] {

  import FileSystemApi._

  /**
    * Writes textual content to a file.
    *
    * @param file path to the file
    * @param content    a textual content of the file
    * @return either FileSystemFailure or Unit
    */
  override def write(
    file: File,
    content: String
  ): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          FileUtils.write(file, content, "UTF-8")
        }
        .leftMap(errorHandling)
    }

  /**
    * Reads the contents of a textual file.
    *
    * @param file path to the file
    * @return either [[FileSystemFailure]] or the content of a file as a String
    */
  override def read(file: File): F[Either[FileSystemFailure, String]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          FileUtils.readFileToString(file, "UTF-8")
        }
        .leftMap(errorHandling)
    }

  /**
    * Deletes the specified file or directory recursively.
    *
    * @param file path to the file or directory
    * @return either [[FileSystemFailure]] or Unit
    */
  def delete(file: File): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          if (file.isDirectory) {
            FileUtils.deleteDirectory(file)
          } else {
            Files.delete(file.toPath)
          }
        }
        .leftMap(errorHandling)
    }

  /**
    * Creates an empty file with parent directory.
    *
    * @param file path to the file
    * @return
    */
  override def createFile(file: File): F[Either[FileSystemFailure, Unit]] = {
    val op =
      for {
        _ <- EitherT { createDirectory(file.getParentFile) }
        _ <- EitherT { createEmptyFile(file)               }
      } yield ()

    op.value
  }

  private def createEmptyFile(file: File): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          file.createNewFile()
        }
        .leftMap(errorHandling)
        .map(_ => ())
    }

  /**
    * Creates a directory, including any necessary but nonexistent parent
    * directories.
    *
    * @param file path to the file
    * @return
    */
  override def createDirectory(
    file: File
  ): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          FileUtils.forceMkdir(file)
        }
        .leftMap(errorHandling)
    }

  /**
    * Copy a file or directory recursively.
    *
    * @param from a path to the source
    * @param to a path to the destination. If from is a file, then to
    * should also be a file. If from is directory, then to should also
    * be a directory.
    * @return either [[FileSystemFailure]] or Unit
    */
  override def copy(
    from: File,
    to: File
  ): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      if (from.isDirectory && to.isFile) {
        Left(FileExists)
      } else {
        Either
          .catchOnly[IOException] {
            if (from.isFile && to.isDirectory) {
              FileUtils.copyFileToDirectory(from, to)
            } else if (from.isDirectory) {
              FileUtils.copyDirectory(from, to)
            } else {
              FileUtils.copyFile(from, to)
            }
          }
      }.leftMap(errorHandling)
    }

  /**
    * Move a file or directory recursively
    *
    * @param from a path to the source
    * @param to a path to the destination
    * @return either [[FileSystemFailure]] or Unit
    */
  override def move(
    from: File,
    to: File
  ): F[Either[FileSystemFailure, Unit]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          if (to.isDirectory) {
            val createDestDir = false
            FileUtils.moveToDirectory(from, to, createDestDir)
          } else if (from.isDirectory) {
            FileUtils.moveDirectory(from, to)
          } else {
            FileUtils.moveFile(from, to)
          }
        }
        .leftMap(errorHandling)
    }

  /**
    * Checks if the specified file exists.
    *
    * @param file path to the file or directory
    * @return either [[FileSystemFailure]] or file existence flag
    */
  override def exists(file: File): F[Either[FileSystemFailure, Boolean]] =
    Sync[F].delay {
      Either
        .catchOnly[IOException] {
          Files.exists(file.toPath)
        }
        .leftMap(errorHandling)
    }

  override def list(path: File): F[Either[FileSystemFailure, Vector[Entry]]] =
    Sync[F].delay {
      if (path.exists) {
        if (path.isDirectory) {
          Either
            .catchOnly[IOException] {
              FileSystem
                .list(path.toPath)
                .map {
                  case SymbolicLinkEntry(path, _) =>
                    FileSystem.readSymbolicLink(path)
                  case entry => entry
                }
            }
            .leftMap(errorHandling)
        } else {
          Left(NotDirectory)
        }
      } else {
        Left(FileNotFound)
      }
    }

  /**
    * Returns contents of a given path.
    *
    * @param path to the file system object
    * @param depth maximum depth of a directory tree
    * @return either [[FileSystemFailure]] or directory structure
    */
  override def tree(
    path: File,
    depth: Option[Int]
  ): F[Either[FileSystemFailure, DirectoryEntry]] = {
    Sync[F].delay {
      val limit = FileSystem.Depth(depth)
      if (path.exists && limit.canGoDeeper) {
        if (path.isDirectory) {
          Either
            .catchOnly[IOException] {
              FileSystem.readDirectoryEntry(path.toPath, limit.goDeeper, Set())
            }
            .leftMap(errorHandling)
        } else {
          Left(NotDirectory)
        }
      } else {
        Left(FileNotFound)
      }
    }
  }

  private val errorHandling: IOException => FileSystemFailure = {
    case _: FileNotFoundException => FileNotFound
    case _: NoSuchFileException   => FileNotFound
    case _: FileExistsException   => FileExists
    case _: AccessDeniedException => AccessDenied
    case ex                       => GenericFileSystemFailure(ex.getMessage)
  }

}

object FileSystem {

  import FileSystemApi._

  /**
    * Represent a depth limit when recursively traversing a directory.
    */
  sealed private trait Depth {

    def canGoDeeper: Boolean

    def goDeeper: Depth
  }

  private object Depth {

    def apply(depth: Option[Int]): Depth =
      depth.fold[Depth](UnlimitedDepth)(LimitedDepth)
  }

  private case class LimitedDepth(limit: Int) extends Depth {

    override def canGoDeeper: Boolean =
      limit > 0

    override def goDeeper: Depth =
      LimitedDepth(limit - 1)
  }

  private case object UnlimitedDepth extends Depth {

    override def canGoDeeper: Boolean =
      true

    override def goDeeper: Depth =
      UnlimitedDepth
  }

  private def readEntry(path: Path): Entry = {
    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      FileEntry(path)
    } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      DirectoryEntryTruncated(path)
    } else if (Files.isSymbolicLink(path)) {
      val target = Files.readSymbolicLink(path)
      if (Files.exists(target)) {
        SymbolicLinkEntry(path, target)
      } else {
        OtherEntry(path)
      }
    } else {
      OtherEntry(path)
    }
  }

  private def readSymbolicLink(path: Path): Entry = {
    if (Files.isRegularFile(path)) {
      FileEntry(path)
    } else if (Files.isDirectory(path)) {
      DirectoryEntryTruncated(path)
    } else {
      OtherEntry(path)
    }
  }

  private def list(path: Path): Vector[Entry] = {
    def accumulator(acc: Vector[Entry], path: Path): Vector[Entry] =
      acc :+ readEntry(path)
    def combiner(as: Vector[Entry], bs: Vector[Entry]): Vector[Entry] =
      as ++ bs
    Files
      .list(path)
      .reduce(Vector(), accumulator, combiner)
      .sortBy(_.path)
  }

  private def readDirectoryEntry(
    path: Path,
    level: Depth,
    visited: Set[Path]
  ): DirectoryEntry = {
    def analyze(level: Depth, visited: Set[Path], child: Entry): Entry =
      child match {
        case DirectoryEntryTruncated(path) =>
          if (level.canGoDeeper) {
            readDirectoryEntry(path, level.goDeeper, visited)
          } else {
            DirectoryEntryTruncated(path)
          }
        case SymbolicLinkEntry(path, target) =>
          if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            if (visited.contains(target)) {
              SymbolicLinkLoop(path)
            } else {
              analyze(level, visited + target, readSymbolicLink(path))
            }
          } else {
            analyze(level, visited, readSymbolicLink(path))
          }
        case entry => entry

      }
    def accumulator(entry: DirectoryEntry, child: Entry): DirectoryEntry =
      entry.copy(children = entry.children :+ analyze(level, visited, child))
    list(path).foldLeft(DirectoryEntry.empty(path))(accumulator)
  }

  /**
    * Return a [[DirectoryEntry]] tree representation of the directory, where
    * the directory depth is limited with the [[Depth]] level.
    *
    * @param path a path to the directory
    * @param level a maximum depth of the directory tree
    * @param visited symlinked directories
    * @return a [[DirectoryEntry]] tree representation of the directory
    */
  private def readDirectoryEntry0(
    path: Path,
    level: Depth,
    visited: Set[Path]
  ): DirectoryEntry = {
    def isVisited(path: Path): Boolean =
      visited.contains(path)
    def readEntry(
      path: Path,
      visited: Set[Path],
      opts: Seq[LinkOption]
    ): Entry = {
      if (Files.isRegularFile(path, opts: _*)) {
        FileEntry(path)
      } else if (Files.isDirectory(path, opts: _*)) {
        if (level.canGoDeeper) {
          readDirectoryEntry(path, level.goDeeper, visited)
        } else {
          DirectoryEntryTruncated(path)
        }
      } else if (Files.isSymbolicLink(path)) {
        val target = Files.readSymbolicLink(path)
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
          if (isVisited(target)) {
            SymbolicLinkLoop(path)
          } else {
            readEntry(path, visited + target, Seq())
          }
        } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
          readEntry(path, visited, Seq())
        } else {
          OtherEntry(path)
        }
      } else {
        OtherEntry(path)
      }
    }
    def accumulator(entry: DirectoryEntry, path: Path): DirectoryEntry =
      entry.copy(
        children = entry.children :+ readEntry(
            path,
            visited,
            Seq(LinkOption.NOFOLLOW_LINKS)
          )
      )
    def combiner(a: DirectoryEntry, b: DirectoryEntry): DirectoryEntry =
      a.copy(children = a.children ++ b.children)
    val entry = Files
      .list(path)
      .reduce(DirectoryEntry.empty(path), accumulator, combiner)
    entry.copy(children = entry.children.sortBy(_.path))
  }

}
