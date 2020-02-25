package org.enso.languageserver.filemanager

import org.enso.languageserver.filemanager.FileManagerApi.{
  AccessDeniedError,
  ContentRootNotFoundError,
  FileSystemError
}
import org.enso.languageserver.jsonrpc.Error

object FileSystemFailureMapper {

  /**
    * Maps [[FileSystemFailure]] into JSON-RPC error.
    *
    * @param fileSystemFailure file system specific failure
    * @return JSON-RPC error
    */
  def mapFailure(fileSystemFailure: FileSystemFailure): Error =
    fileSystemFailure match {
      case ContentRootNotFound              => ContentRootNotFoundError
      case AccessDenied                     => AccessDeniedError
      case GenericFileSystemFailure(reason) => FileSystemError(reason)
    }

}
