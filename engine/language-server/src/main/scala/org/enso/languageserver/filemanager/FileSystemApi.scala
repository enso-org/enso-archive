package org.enso.languageserver.filemanager

trait FileSystemApi[F[_]] {

  def write(
    pathString: String,
    content: String
  ): F[Either[FileSystemFailure, Unit]]

}
