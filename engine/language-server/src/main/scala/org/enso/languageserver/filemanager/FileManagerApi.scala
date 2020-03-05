package org.enso.languageserver.filemanager

import org.enso.languageserver.jsonrpc.{
  Error,
  HasParams,
  HasResult,
  Method,
  Unused
}

/**
  * The file manager JSON RPC API provided by the language server.
  * See [[https://github.com/luna/enso/blob/master/doc/design/engine/engine-services.md]]
  * for message specifications.
  */
object FileManagerApi {

  case object WriteFile extends Method("file/write") {

    case class Params(path: Path, contents: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = WriteFile.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case object ReadFile extends Method("file/read") {

    case class Params(path: Path)

    case class Result(contents: String)

    implicit val hasParams = new HasParams[this.type] {
      type Params = ReadFile.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = ReadFile.Result
    }
  }

  case object CreateFile extends Method("file/create") {

    case class Params(`object`: FileSystemObject)

    implicit val hasParams = new HasParams[this.type] {
      type Params = CreateFile.Params
    }
    implicit val hasResult = new HasResult[this.type] {
      type Result = Unused.type
    }
  }

  case class FileSystemError(override val message: String)
      extends Error(1000, message)

  case object ContentRootNotFoundError
      extends Error(1001, "Content root not found")

  case object AccessDeniedError extends Error(1002, "Access denied")

  case object FileNotFoundError extends Error(1003, "File not found")

  case object OperationTimeoutError extends Error(1003, "IO operation timeout")

}
