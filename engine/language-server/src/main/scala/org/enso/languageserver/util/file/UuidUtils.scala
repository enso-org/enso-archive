package org.enso.languageserver.util.file

import java.util.UUID

import org.enso.languageserver.protocol.data.util.EnsoUUID

object UuidUtils {

  def convertEnsoUuid(uuid: EnsoUUID): UUID =
    new UUID(uuid.mostSigBits(), uuid.leastSigBits())

}
