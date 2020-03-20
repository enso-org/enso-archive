package org.enso.projectmanager.infrastructure.file

import java.io.File

trait FileSystem[F[_, _]] {

  def readFile(file: File): F[FileSystemFailure, String]

  def overwriteFile(
    file: File,
    contents: String
  ): F[FileSystemFailure, Unit]

  def removeDir(path: File): F[FileSystemFailure, Unit]

}
