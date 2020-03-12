package org.enso.languageserver.filemanager

import java.io.File
import java.nio

/**
  * A representation of tree structures of files and directories.
  *
  * @param path to the directory
  * @param name of the directory
  * @param files contents of the directory
  * @param directories contents of the directory
  */
case class DirectoryTree(
  path: Path,
  name: String,
  files: Vector[FileSystemObject],
  directories: Vector[DirectoryTree]
)

object DirectoryTree {

  /**
    * Create [[DirectoryTree]] from [[FileSystemApi.DirectoryEntry]]
    * converting absolute pathes to ones relative to project root.
    *
    * @param root path to the project root
    * @param base path to the required directory
    * @param directory a [[FileSystemApi]] representation of a directory
    * @return a directory tree with paths relative to project root
    */
  def fromDirectoryEntry(
    root: File,
    base: Path,
    directory: FileSystemApi.DirectoryEntry
  ): DirectoryTree =
    DirectoryTree(
      path = mkRelativeParent(root, base, directory.path),
      name = directory.path.getFileName.toString,
      files = directory.children
        .filterNot(isDirectoryEntry)
        .map(mkFileSystemObject(root, base, _)),
      directories = directory.children.flatMap(fromEntry(root, base, _))
    )

  private def isDirectoryEntry(entry: FileSystemApi.Entry): Boolean =
    entry match {
      case _: FileSystemApi.DirectoryEntry => true
      case _                               => false
    }

  private def fromEntry(
    root: File,
    base: Path,
    entry: FileSystemApi.Entry
  ): Option[DirectoryTree] =
    entry match {
      case dir: FileSystemApi.DirectoryEntry =>
        Some(fromDirectoryEntry(root, base, dir))
      case _ => None
    }

  private def mkFileSystemObject(
    root: File,
    base: Path,
    entry: FileSystemApi.Entry
  ): FileSystemObject =
    entry match {
      case FileSystemApi.DirectoryEntry(path, _) =>
        FileSystemObject.Directory(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.DirectoryEntryTruncated(path) =>
        FileSystemObject.DirectoryTruncated(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.SymbolicLinkLoop(path) =>
        FileSystemObject.SymlinkLoop(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.FileEntry(path) =>
        FileSystemObject.File(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.OtherEntry(path) =>
        FileSystemObject.Other(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )
    }

  private def mkRelativePath(
    root: File,
    base: Path,
    path: nio.file.Path
  ): Path =
    Path(base.rootId, root.toPath.relativize(path))

  private def mkRelativeParent(
    root: File,
    base: Path,
    path: nio.file.Path
  ): Path =
    mkRelativePath(root, base, path.getParent())
}
