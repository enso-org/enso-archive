package org.enso.languageserver.filemanager

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

  def empty(path: Path): DirectoryTree =
    DirectoryTree(
      path        = path,
      name        = path.getName,
      files       = Vector.empty,
      directories = Vector.empty
    )

  def fromEntry(
    path: Path,
    entry: FileSystemApi.Entry
  ): Either[FileSystemFailure, DirectoryTree] =
    ???

}
