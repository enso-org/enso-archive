package org.enso.languageserver.filemanager

import org.enso.languageserver.filemanager.failures.WriteFailure

trait FileSystemApi[F[_]] {

  def write(path: String, content: String): F[Either[WriteFailure, Unit]]

}
