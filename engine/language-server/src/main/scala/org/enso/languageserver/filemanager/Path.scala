package org.enso.languageserver.filemanager

import java.util.UUID

case class Path(rootId: UUID, segments: List[String])
