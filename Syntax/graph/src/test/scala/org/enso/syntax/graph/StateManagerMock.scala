package org.enso.syntax.graph

import org.enso.syntax.graph.API.Module
import org.enso.syntax.graph.SessionManager.Metadata
import org.enso.syntax.text.AST

/** Mock project state - contains a single module named `Main` with given
  * program body text.
  *
  * Initially no metadata is present, however whatever is set, will be visible
  * afterwards.
  */
final case class StateManagerMock(var program: String) extends StateManager {
  var ast: AST.Module                              = ParserUtils.parse(program)
  var metadata: Map[(Module.ID, AST.ID), Metadata] = Map()

  override def moduleInProject(): Seq[Module.ID] =
    Seq(StateManagerMock.mainModule)

  override def getModule(module: Module.ID): AST.Module = {
    if (module == StateManagerMock.mainModule) ast
    else throw StateManager.ModuleNotFoundException(module)
  }

  override def setModule(module: Module.ID, ast: AST.Module): Unit = {
    if (module != StateManagerMock.mainModule)
      throw new Exception(s"no such module: $module")

    this.program = ast.show()
    this.ast     = ast
    println(s"New AST for module $module: $ast")
    println(s"New Program text for module $module:\n$program")
  }

  override def getMetadata(module: Module.ID, id: AST.ID): Option[Metadata] =
    metadata.get(module -> id)

  override def setMetadata(
    module: Module.ID,
    id: AST.ID,
    newMetadata: Metadata
  ): Unit = {
    metadata = metadata + (module -> id -> newMetadata)
  }

  override def removeMetadata(module: Module.ID, id: AST.ID): Unit =
    metadata = metadata - (module -> id)
}

object StateManagerMock {
  val mainModule: Module.ID = Module.Name("Main")
}
