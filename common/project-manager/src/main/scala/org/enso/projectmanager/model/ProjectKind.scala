package org.enso.projectmanager.model

import enumeratum._

sealed trait ProjectKind extends EnumEntry

object ProjectKind extends Enum[ProjectKind] with CirceEnum[ProjectKind] {

  case object UserProject extends ProjectKind

  override def values: IndexedSeq[ProjectKind] = findValues

}
