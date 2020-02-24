package org.enso.languageserver.filemanager

object FileManagerProtocol {

  case class FileWrite(path: Path, content: String)

  case class FileOperationResult(result: Either[FileSystemFailure, Unit])

}
