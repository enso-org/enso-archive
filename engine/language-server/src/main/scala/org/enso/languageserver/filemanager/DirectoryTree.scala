package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.Paths

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
  files: Set[FileSystemObject],
  directories: Set[DirectoryTree]
)

object DirectoryTree {

  def fromDirectoryEntry(
    root: File,
    base: Path,
    directory: FileSystemApi.DirectoryEntry
  ): DirectoryTree =
    DirectoryTree(
      path =
        Path(base.rootId, relativizeEntryPath(root, getParent(directory.path))),
      name        = directory.path.getFileName.toString,
      files       = directory.children.map(toFileSystemObject(root, base, _)),
      directories = directory.children.flatMap(fromEntry(root, base, _))
    )

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

  private def relativizeEntryPath(
    root: File,
    file: java.nio.file.Path
  ): java.nio.file.Path =
    root.toPath.relativize(file)

  private def relativizeSymlinkEntryPath(
    root: File,
    file: java.nio.file.Path
  ): java.nio.file.Path = {
    if (file.startsWith(root.toPath)) {
      relativizeEntryPath(root, file)
    } else {
      file
    }
  }

  private def getParent(path: java.nio.file.Path): java.nio.file.Path =
    Option(path.getParent()).getOrElse(Paths.get(""))

  private def toFileSystemObject(
    root: File,
    base: Path,
    entry: FileSystemApi.Entry
  ): FileSystemObject = {
    def getRelativeParent(path: java.nio.file.Path): java.nio.file.Path =
      // getParent(relativizeEntryPath(root, path))
      relativizeEntryPath(root, getParent(path))
    entry match {
      case FileSystemApi.DirectoryEntry(path, _) =>
        FileSystemObject.Directory(
          path.getFileName.toString,
          Path(base.rootId, getRelativeParent(path))
        )

      case FileSystemApi.FileEntry(path) =>
        FileSystemObject.File(
          path.getFileName.toString,
          Path(base.rootId, getRelativeParent(path))
        )

      case FileSystemApi.SymbolicLinkEntry(path, target) =>
        FileSystemObject.Symlink(
          Path(base.rootId, relativizeSymlinkEntryPath(root, path)),
          Path(base.rootId, relativizeSymlinkEntryPath(root, target))
        )

      case FileSystemApi.OtherEntry(_) =>
        FileSystemObject.Other
    }

  }

}
