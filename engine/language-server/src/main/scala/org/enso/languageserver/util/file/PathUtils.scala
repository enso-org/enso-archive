package org.enso.languageserver.util.file

import org.enso.languageserver.filemanager.Path
import org.enso.languageserver.protocol.data.filemanager.{Path => BinaryPath}

object PathUtils {

  def convertBinaryPath(path: BinaryPath): Path = {
    val rootId = UuidUtils.convertEnsoUuid(path.rootId())
    val segments =
      (0 until path.segmentsLength()).foldRight(List.empty[String]) {
        case (index, tail) => path.segmentsVector().get(index) :: tail
      }

    Path(rootId, segments.toVector)
  }

}
