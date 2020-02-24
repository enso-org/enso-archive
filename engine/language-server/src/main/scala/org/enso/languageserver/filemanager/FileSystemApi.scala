package org.enso.languageserver.filemanager

/**
  * File manipulation API.
  *
  * @tparam F represents target monad
  */
trait FileSystemApi[F[_]] {

  /**
    * Writes textual content to a file.
    *
    * @param pathString path to the file
    * @param content a textual content of the file
    * @return either FileSystemFailure or Unit
    */
  def write(
    pathString: String,
    content: String
  ): F[Either[FileSystemFailure, Unit]]

}
