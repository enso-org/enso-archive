package org.enso.projectmanager.infrastructure.repo

import java.time.OffsetDateTime
import java.util.UUID

case class ProjectMetadata(
  id: UUID,
  name: String,
  path: String,
  created: OffsetDateTime,
  lastOpened: Option[OffsetDateTime]
)
