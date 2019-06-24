package org.enso.interpreter

import com.oracle.truffle.api.TruffleFile
import java.io.IOException
import java.nio.charset.Charset

final class FileDetector extends TruffleFile.FileTypeDetector {

  @throws[IOException]
  override def findMimeType(file: TruffleFile): String = {
    val name = file.getName
    if (name != null && name.endsWith(Constants.FILE_EXTENSION))
      return Constants.MIME_TYPE
    null
  }

  @throws[IOException]
  override def findEncoding(file: TruffleFile): Charset = {
    // TODO [AA] Give this a proper implementation.
    null
  }
}
