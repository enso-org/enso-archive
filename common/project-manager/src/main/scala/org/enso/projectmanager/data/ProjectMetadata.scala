package org.enso.projectmanager.data

import java.time.OffsetDateTime
import java.util.UUID

case class ProjectMetadata(
  name: String,
  id: UUID,
  lastOpened: Option[OffsetDateTime]
)
