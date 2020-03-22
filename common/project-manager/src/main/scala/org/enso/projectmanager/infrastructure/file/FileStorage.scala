package org.enso.projectmanager.infrastructure.file

import org.enso.projectmanager.infrastructure.file.FileStorage._
import shapeless._

trait FileStorage[A, F[_, _]] {

  def load(): F[LoadFailure, A]

  def persist(data: A): F[FileSystemFailure, Unit]

  def modify[B](f: A => (A, B)): F[LoadFailure, B]

}

object FileStorage {

  case class CannotDecodeData(msg: String)

  type LoadFailure = CannotDecodeData :+: FileSystemFailure :+: CNil

}
