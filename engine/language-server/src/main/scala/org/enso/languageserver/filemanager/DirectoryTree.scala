package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.{Path, Paths}

/**
  * A representation of tree structures of files and directories.
  *
  * @param path to the directory
  * @param name of the directory
  * @param files contents of the directory
  * @param directories contents of the directory
  */
case class DirectoryTree(
  path: RelativePath,
  name: String,
  files: Set[FileSystemObject],
  directories: Set[DirectoryTree]
)

object DirectoryTree {

  def fromDirectoryEntry(
    root: File,
    base: RelativePath,
    directory: FileSystemApi.DirectoryEntry
  ): DirectoryTree =
    DirectoryTree(
      path        = relativizeEntryPath(root, base, getParent(directory.path)),
      name        = directory.path.getFileName.toString,
      files       = directory.children.map(toFileSystemObject(root, base, _)),
      directories = directory.children.flatMap(fromEntry(root, base, _))
    )

  private def fromEntry(
    root: File,
    base: RelativePath,
    entry: FileSystemApi.Entry
  ): Option[DirectoryTree] =
    entry match {
      case dir: FileSystemApi.DirectoryEntry =>
        Some(fromDirectoryEntry(root, base, dir))
      case _ => None
    }

  private def relativizeEntryPath(
    root: File,
    base: RelativePath,
    file: java.nio.file.Path
  ): RelativePath =
    RelativePath(base.rootId, root.toPath.relativize(file))

  private def relativizeSymlinkEntryPath(
    root: File,
    base: RelativePath,
    file: java.nio.file.Path
  ): Segments = {
    if (file.startsWith(root.toPath)) {
      relativizeEntryPath(root, base, file)
    } else {
      AbsolutePath(file)
    }
  }

  private def getParent(path: java.nio.file.Path): java.nio.file.Path =
    Option(path.getParent()).getOrElse(Paths.get(""))

  private def toFileSystemObject(
    root: File,
    base: RelativePath,
    entry: FileSystemApi.Entry
  ): FileSystemObject = {
    def getRelativeParent(path: Path): RelativePath =
      relativizeEntryPath(root, base, getParent(path))
    entry match {
      case FileSystemApi.DirectoryEntry(path, _) =>
        FileSystemObject.Directory(
          path.getFileName.toString,
          getRelativeParent(path)
        )

      case FileSystemApi.FileEntry(path) =>
        FileSystemObject.File(
          path.getFileName.toString,
          getRelativeParent(path)
        )

      case FileSystemApi.SymbolicLinkEntry(path, target) =>
        FileSystemObject.Symlink(
          relativizeEntryPath(root, base, path),
          relativizeSymlinkEntryPath(root, base, target)
        )

      case FileSystemApi.OtherEntry(_) =>
        FileSystemObject.Other
    }

  }

}
