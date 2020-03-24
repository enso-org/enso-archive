package org.enso.languageserver.filemanager

import java.time.OffsetDateTime

/**
  * A representation of UTC time.
  *
  * @param time ISO-8601 string
  */
case class UTCDateTime(time: OffsetDateTime)
