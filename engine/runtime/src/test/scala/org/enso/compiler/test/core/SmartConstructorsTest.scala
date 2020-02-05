package org.enso.compiler.test.core

import org.enso.core.CoreGraph.{DefinitionGen => CoreDef}
import org.enso.graph.{Graph => PrimGraph}
import org.enso.compiler.core.Core
import org.enso.compiler.test.CompilerTest
import org.scalatest.BeforeAndAfterEach

class SmartConstructorsTest extends CompilerTest with BeforeAndAfterEach {

  // === Test Setup ===========================================================

  import CoreDef.Link.Shape._
  import CoreDef.Node.Location._
  import CoreDef.Node.ParentLinks._
  import CoreDef.Node.Shape._
  import PrimGraph.Component.Refined._
  import Core._

  implicit var core: Core = _

  override def beforeEach(): Unit = {
    core = new Core()
  }

  // === Tests for Node Smart Constructors ====================================

  // === Tests for Node Utility Functions =====================================

  // === Tests for Link Smart Constructors ====================================

  "Links" should "be able to be made with a source and target" in {
    val n1 = Core.Node.New.empty()
    val n2 = Core.Node.New.empty()

    val link = Core.Link.New.connected(n1, n2)

    link.source shouldEqual n1
    link.target shouldEqual n2
  }

}
