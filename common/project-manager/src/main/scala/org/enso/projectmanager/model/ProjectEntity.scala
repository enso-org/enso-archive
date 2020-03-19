package org.enso.projectmanager.model

import java.time.OffsetDateTime
import java.util.UUID

case class ProjectEntity(
  id: UUID,
  name: String,
  created: OffsetDateTime,
  lastOpened: Option[OffsetDateTime]
)
