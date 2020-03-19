package org.enso.projectmanager.infrastructure.file

import java.io.File

import zio.{ZEnv, ZIO}

trait FileSystemApi {

  def readFile(file: File): ZIO[ZEnv, FileSystemFailure, String]

  def overwriteFile(
    file: File,
    contents: String
  ): ZIO[ZEnv, FileSystemFailure, Unit]

}
