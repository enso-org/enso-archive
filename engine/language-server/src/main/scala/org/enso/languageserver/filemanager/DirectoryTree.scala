package org.enso.languageserver.filemanager

import java.io.File
import java.nio.file.Path

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
      path        = mkRelativeParent(root, base, directory.path),
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

  private def toFileSystemObject(
    root: File,
    base: RelativePath,
    entry: FileSystemApi.Entry
  ): FileSystemObject =
    entry match {
      case FileSystemApi.DirectoryEntry(path, _) =>
        FileSystemObject.Directory(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.FileEntry(path) =>
        FileSystemObject.File(
          path.getFileName.toString,
          mkRelativeParent(root, base, path)
        )

      case FileSystemApi.SymbolicLinkEntry(path, target) =>
        FileSystemObject.Symlink(
          mkRelativePath(root, base, path),
          mkSymlinkTargetPath(root, base, target)
        )

      case FileSystemApi.OtherEntry(_) =>
        FileSystemObject.Other
    }

  private def mkRelativePath(
    root: File,
    base: RelativePath,
    path: Path
  ): RelativePath =
    RelativePath(base.rootId, root.toPath.relativize(path))

  private def mkRelativeParent(
    root: File,
    base: RelativePath,
    path: Path
  ): RelativePath =
    mkRelativePath(root, base, path.getParent())

  private def mkSymlinkTargetPath(
    root: File,
    base: RelativePath,
    path: Path
  ): SystemPath =
    if (path.startsWith(root.toPath)) {
      mkRelativePath(root, base, path)
    } else {
      AbsolutePath(path)
    }

}