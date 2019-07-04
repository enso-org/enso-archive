package org.enso.syntax

import org.enso.GroupsCreator
import scala.language.experimental.macros

class GeneratedParser extends Parser {

  val runMacro: Unit = createGroups()

  def createGroups(): Unit = macro GroupsCreator.createGroups

}