package org.enso.languageserver.filemanager

import cats.data.EitherT
import org.enso.languageserver.filemanager.failures.WriteFailure

trait FileSystem[F[_]] {

  def write(path: String, content: String): EitherT[F, WriteFailure, Unit]
}
