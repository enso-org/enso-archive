package org.enso.projectmanager.model

import java.time.OffsetDateTime
import java.util.UUID

case class Project(
  id: UUID,
  name: String,
  created: OffsetDateTime,
  lastOpened: Option[OffsetDateTime] = None,
  path: Option[String]               = None
)
