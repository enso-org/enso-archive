package org.enso.projectmanager.model

import enumeratum._

/**
  * Distinguishes between different kinds of projects.
  */
sealed trait ProjectKind extends EnumEntry

object ProjectKind extends Enum[ProjectKind] with CirceEnum[ProjectKind] {

  /**
    * Tags user projects.
    */
  case object UserProject extends ProjectKind

  override def values: IndexedSeq[ProjectKind] = findValues

}
